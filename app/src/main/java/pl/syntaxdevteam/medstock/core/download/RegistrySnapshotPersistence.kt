package pl.syntaxdevteam.medstock.core.download

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Log
import java.security.MessageDigest

/**
 * Zapisuje sparsowany snapshot źródła do tabel staging z obsługą snapshot_date_utc.
 */
class RegistrySnapshotPersistence(
    private val database: SQLiteDatabase,
    private val parserVersion: String = "1"
) {
    private val tag = "RegistryPersistence"

    fun saveSnapshot(
        source: RegistryFileSource,
        sourceUrl: String,
        fileName: String?,
        fileSha256: String,
        snapshotDateUtc: String,
        fetchedAtUtc: String,
        parsed: ParsedRegistryFile,
        sourceLastModifiedUtc: String? = null,
        sourceEtag: String? = null
    ): Long {
        require(snapshotDateUtc.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            "snapshotDateUtc musi mieć format YYYY-MM-DD"
        }

        database.beginTransaction()
        try {
            val batchId = upsertBatch(
                source = source,
                sourceUrl = sourceUrl,
                fileName = fileName,
                fileSha256 = fileSha256,
                snapshotDateUtc = snapshotDateUtc,
                fetchedAtUtc = fetchedAtUtc,
                sourceLastModifiedUtc = sourceLastModifiedUtc,
                sourceEtag = sourceEtag
            )

            clearBatchRows(batchId)
            val resolvedHeaders = resolveHeaders(parsed.headers)
            val columnIdByKey = ensureColumns(source, batchId, resolvedHeaders)
            val columnIdsByPosition = resolvedHeaders.map { header -> columnIdByKey[header.trim()] }
            val rowInsertStatement = database.compileStatement(
                """
                INSERT INTO registry_row(batch_id, source_row_number, source_entity_key, row_hash)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
            )
            val cellInsertStatement = database.compileStatement(
                "INSERT OR REPLACE INTO registry_cell(row_id, column_id, value_text) VALUES (?, ?, ?)"
            )

            var savedRows = 0
            parsed.records.forEach { record ->
                val rowId = insertRow(
                    statement = rowInsertStatement,
                    batchId = batchId,
                    sourceRowNumber = record.rowNumber,
                    sourceEntityKey = detectEntityKey(source, parsed.headers, record.values),
                    rowHash = calculateRowHash(record.values)
                )
                insertCells(
                    statement = cellInsertStatement,
                    rowId = rowId,
                    columnIdsByPosition = columnIdsByPosition,
                    values = record.values
                )
                savedRows += 1
                if (savedRows % LOG_PROGRESS_EVERY_ROWS == 0) {
                    Log.i(tag, "Persist progress source=${source.name} batchId=$batchId rows=$savedRows")
                }
            }

            updateBatchRecordCount(batchId, savedRows)
            Log.i(tag, "Saved snapshot source=${source.name} batchId=$batchId snapshotDate=$snapshotDateUtc rows=$savedRows")

            database.setTransactionSuccessful()
            return batchId
        } finally {
            database.endTransaction()
        }
    }

    private fun upsertBatch(
        source: RegistryFileSource,
        sourceUrl: String,
        fileName: String?,
        fileSha256: String,
        snapshotDateUtc: String,
        fetchedAtUtc: String,
        sourceLastModifiedUtc: String?,
        sourceEtag: String?
    ): Long {
        database.execSQL(
            """
            INSERT INTO registry_import_batch(
                source_code, snapshot_date_utc, source_url, file_name,
                source_last_modified_utc, source_etag, file_sha256,
                fetched_at_utc, parser_version, record_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT(source_code, snapshot_date_utc) DO UPDATE SET
                source_url = excluded.source_url,
                file_name = excluded.file_name,
                source_last_modified_utc = excluded.source_last_modified_utc,
                source_etag = excluded.source_etag,
                file_sha256 = excluded.file_sha256,
                fetched_at_utc = excluded.fetched_at_utc,
                parser_version = excluded.parser_version,
                record_count = 0
            """.trimIndent(),
            arrayOf<Any?>(
                source.name,
                snapshotDateUtc,
                sourceUrl,
                fileName,
                sourceLastModifiedUtc,
                sourceEtag,
                fileSha256,
                fetchedAtUtc,
                parserVersion
            )
        )

        database.rawQuery(
            "SELECT id FROM registry_import_batch WHERE source_code = ? AND snapshot_date_utc = ?",
            arrayOf(source.name, snapshotDateUtc)
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Nie znaleziono batcha po UPSERT." }
            return cursor.getLong(0)
        }
    }

    private fun clearBatchRows(batchId: Long) {
        database.execSQL(
            "DELETE FROM registry_row WHERE batch_id = ?",
            arrayOf<Any?>(batchId)
        )
    }

    private fun ensureColumns(
        source: RegistryFileSource,
        batchId: Long,
        headers: List<String>
    ): Map<String, Long> {
        val normalizedHeaders = headers.map { it.trim() }
        normalizedHeaders.forEach { key ->
            database.execSQL(
                """
                INSERT INTO registry_column_dictionary(source_code, column_key, first_seen_batch_id)
                VALUES (?, ?, ?)
                ON CONFLICT(source_code, column_key) DO NOTHING
                """.trimIndent(),
                arrayOf<Any?>(source.name, key, batchId)
            )
        }

        val result = mutableMapOf<String, Long>()
        database.rawQuery(
            "SELECT id, column_key FROM registry_column_dictionary WHERE source_code = ?",
            arrayOf(source.name)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(1)] = cursor.getLong(0)
            }
        }

        return result
    }

    private fun insertRow(
        statement: SQLiteStatement,
        batchId: Long,
        sourceRowNumber: Long,
        sourceEntityKey: String?,
        rowHash: String
    ): Long {
        statement.clearBindings()
        statement.bindLong(1, batchId)
        statement.bindLong(2, sourceRowNumber)
        if (sourceEntityKey == null) statement.bindNull(3) else statement.bindString(3, sourceEntityKey)
        statement.bindString(4, rowHash)
        return statement.executeInsert().also { rowId ->
            check(rowId > 0L) { "Brak row_id po INSERT." }
        }
    }

    private fun insertCells(
        statement: SQLiteStatement,
        rowId: Long,
        columnIdsByPosition: List<Long?>,
        values: List<String>
    ) {
        columnIdsByPosition.forEachIndexed { index, columnId ->
            if (columnId == null) return@forEachIndexed
            val value = values.getOrNull(index)
            statement.clearBindings()
            statement.bindLong(1, rowId)
            statement.bindLong(2, columnId)
            if (value == null) statement.bindNull(3) else statement.bindString(3, value)
            statement.executeInsert()
        }
    }

    private fun resolveHeaders(headers: List<String>): List<String> {
        val occurrences = mutableMapOf<String, Int>()
        return headers.mapIndexed { index, rawHeader ->
            val base = rawHeader.trim().ifBlank { "kolumna_${index + 1}" }
            val count = (occurrences[base] ?: 0) + 1
            occurrences[base] = count
            if (count == 1) base else "${base}__$count"
        }
    }

    private fun updateBatchRecordCount(batchId: Long, count: Int) {
        database.execSQL(
            "UPDATE registry_import_batch SET record_count = ? WHERE id = ?",
            arrayOf<Any?>(count, batchId)
        )
    }

    private fun detectEntityKey(source: RegistryFileSource, headers: List<String>, values: List<String>): String? {
        val preferredHeader = when (source) {
            RegistryFileSource.RA_CSV,
            RegistryFileSource.RA_XLS -> "identyfikator_apteki"
            RegistryFileSource.RPL_CSV,
            RegistryFileSource.RPL_XLSX -> "Identyfikator Produktu Leczniczego"
            RegistryFileSource.RDG_XML -> "NumerDecyzji"
        }

        val index = headers.indexOfFirst { it.trim().equals(preferredHeader, ignoreCase = true) }
        return values.getOrNull(index)?.takeIf { it.isNotBlank() }
    }

    private fun calculateRowHash(values: List<String>): String {
        val normalized = values.joinToString(separator = "\u001F") { it.trim() }
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val LOG_PROGRESS_EVERY_ROWS = 5_000
    }
}
