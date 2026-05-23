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
            val tables = tablesFor(source)
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

            clearBatchRows(batchId, tables)
            val resolvedHeaders = resolveHeaders(parsed.headers)
            val columnIdByKey = ensureColumns(batchId, resolvedHeaders, tables)
            val columnIdsByPosition = resolvedHeaders.map { header -> columnIdByKey[header.trim()] }
            val rowInsertStatement = database.compileStatement(
                """
                INSERT INTO ${tables.rowTable}(batch_id, source_row_number, source_entity_key, row_hash)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
            )
            val cellInsertStatement = database.compileStatement(
                "INSERT OR REPLACE INTO ${tables.cellTable}(row_id, column_id, value_text) VALUES (?, ?, ?)"
            )

            var savedRows = 0
            val canonicalHeaders = resolvedHeaders.map { canonicalizeHeader(it) }
            val rplProjection = if (source == RegistryFileSource.RPL_CSV || source == RegistryFileSource.RPL_XLSX) {
                prepareProjection(canonicalHeaders, rplHeaderMap)
            } else null
            val raProjection = if (source == RegistryFileSource.RA_CSV || source == RegistryFileSource.RA_XLS) {
                prepareProjection(canonicalHeaders, raHeaderMap)
            } else null
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
                when (source) {
                    RegistryFileSource.RPL_CSV, RegistryFileSource.RPL_XLSX -> {
                        insertRplSnapshot(database, batchId, record.rowNumber, detectEntityKey(source, parsed.headers, record.values), record.values, rplProjection)
                    }
                    RegistryFileSource.RA_CSV, RegistryFileSource.RA_XLS -> {
                        insertRaSnapshot(database, batchId, record.rowNumber, detectEntityKey(source, parsed.headers, record.values), record.values, raProjection)
                    }
                    RegistryFileSource.RDG_XML -> Unit
                }
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

    private fun clearBatchRows(batchId: Long, tables: RegistryTables) {
        database.execSQL(
            "DELETE FROM ${tables.rowTable} WHERE batch_id = ?",
            arrayOf<Any?>(batchId)
        )
        database.execSQL("DELETE FROM registry_rpl_snapshot WHERE batch_id = ?", arrayOf<Any?>(batchId))
        database.execSQL("DELETE FROM registry_ra_snapshot WHERE batch_id = ?", arrayOf<Any?>(batchId))
    }

    private fun ensureColumns(
        batchId: Long,
        headers: List<String>,
        tables: RegistryTables
    ): Map<String, Long> {
        val normalizedHeaders = headers.map { it.trim() }
        normalizedHeaders.forEach { key ->
            database.execSQL(
                """
                INSERT INTO ${tables.columnTable}(column_key, first_seen_batch_id)
                VALUES (?, ?)
                ON CONFLICT(column_key) DO NOTHING
                """.trimIndent(),
                arrayOf<Any?>(key, batchId)
            )
        }

        val result = mutableMapOf<String, Long>()
        database.rawQuery(
            "SELECT id, column_key FROM ${tables.columnTable}",
            emptyArray()
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

        val rplHeaderMap = linkedMapOf(
            "identyfikator_produktu_leczniczego" to listOf("identyfikator_produktu_leczniczego"),
            "nazwa_produktu_leczniczego" to listOf("nazwa_produktu_leczniczego"),
            "droga_podania_gatunek_tkanka_okres_karencji" to listOf("droga_podania_gatunek_tkanka_okres_karencji"),
            "moc" to listOf("moc", "moc_dawki"),
            "postac_farmaceutyczna" to listOf("postac_farmaceutyczna"),
            "podmiot_odpowiedzialny" to listOf("podmiot_odpowiedzialny"),
            "kod_ean" to listOf("kod_ean_udi_di", "kod_ean"),
            "opakowanie" to listOf("opakowanie", "wielkosc_opakowania"),
            "substancja_czynna" to listOf("substancja_czynna", "nazwa_powszechnie_stosowana"),
            "kraj_wytworcy" to listOf("kraj_wytworcy"),
            "ulotka" to listOf("ulotka"),
            "charakterystyka" to listOf("charakterystyka")
        )

        val raHeaderMap = linkedMapOf(
            "nazwa_apteki" to listOf("nazwa_apteki"),
            "stan_apteki" to listOf("stan_apteki", "status_apteki"),
            "wojewodztwo" to listOf("wojewodztwo"),
            "powiat" to listOf("powiat"),
            "gmina" to listOf("gmina"),
            "typ_ulicy" to listOf("typ_ulicy"),
            "nazwa_ulicy" to listOf("nazwa_ulicy"),
            "numer_budynku" to listOf("numer_budynku"),
            "numer_lokalu" to listOf("numer_lokalu"),
            "miejscowosc" to listOf("miejscowosc"),
            "kod_pocztowy" to listOf("kod_pocztowy"),
            "telefon" to listOf("telefon"),
            "fax" to listOf("fax"),
            "email" to listOf("email", "e_mail"),
            "adres_www" to listOf("adres_www", "www"),
            "czy_sprzedaz_wysylkowa" to listOf("czy_sprzedaz_wysylkowa", "sprzedaz_wysylkowa"),
            "godziny_otwarcia_poniedzialek" to listOf("godziny_otwarcia_poniedzialek"),
            "godziny_otwarcia_wtorek" to listOf("godziny_otwarcia_wtorek"),
            "godziny_otwarcia_sroda" to listOf("godziny_otwarcia_sroda"),
            "godziny_otwarcia_czwartek" to listOf("godziny_otwarcia_czwartek"),
            "godziny_otwarcia_piatek" to listOf("godziny_otwarcia_piatek"),
            "godziny_otwarcia_sobota" to listOf("godziny_otwarcia_sobota"),
            "godziny_otwarcia_niedziela_handlowa" to listOf("godziny_otwarcia_niedziela_handlowa"),
            "godziny_otwarcia_niedziela_niehandlowa" to listOf("godziny_otwarcia_niedziela_niehandlowa")
        )
    }
}

private fun canonicalizeHeader(input: String): String = input
    .lowercase()
    .replace("ł", "l")
    .replace("ą", "a")
    .replace("ć", "c")
    .replace("ę", "e")
    .replace("ń", "n")
    .replace("ó", "o")
    .replace("ś", "s")
    .replace("ż", "z")
    .replace("ź", "z")
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')

private fun prepareProjection(
    canonicalHeaders: List<String>,
    mapping: Map<String, List<String>>
): Map<String, Int> = mapping.mapNotNull { (target, aliases) ->
    val idx = canonicalHeaders.indexOfFirst { it in aliases }
    if (idx >= 0) target to idx else null
}.toMap()

private fun insertRplSnapshot(
    database: SQLiteDatabase,
    batchId: Long,
    sourceRowNumber: Long,
    sourceEntityKey: String?,
    values: List<String>,
    projection: Map<String, Int>?
) {
    val p = projection.orEmpty()
    database.execSQL(
        """
        INSERT INTO registry_rpl_snapshot(
            batch_id, source_row_number, source_entity_key, identyfikator_produktu_leczniczego,
            nazwa_produktu_leczniczego, droga_podania_gatunek_tkanka_okres_karencji, moc,
            postac_farmaceutyczna, podmiot_odpowiedzialny, kod_ean, opakowanie, substancja_czynna,
            kraj_wytworcy, ulotka, charakterystyka
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        arrayOf<Any?>(
            batchId, sourceRowNumber, sourceEntityKey,
            values.getOrNull(p["identyfikator_produktu_leczniczego"] ?: -1),
            values.getOrNull(p["nazwa_produktu_leczniczego"] ?: -1),
            values.getOrNull(p["droga_podania_gatunek_tkanka_okres_karencji"] ?: -1),
            values.getOrNull(p["moc"] ?: -1),
            values.getOrNull(p["postac_farmaceutyczna"] ?: -1),
            values.getOrNull(p["podmiot_odpowiedzialny"] ?: -1),
            values.getOrNull(p["kod_ean"] ?: -1),
            values.getOrNull(p["opakowanie"] ?: -1),
            values.getOrNull(p["substancja_czynna"] ?: -1),
            values.getOrNull(p["kraj_wytworcy"] ?: -1),
            values.getOrNull(p["ulotka"] ?: -1),
            values.getOrNull(p["charakterystyka"] ?: -1)
        )
    )
}

private fun insertRaSnapshot(
    database: SQLiteDatabase,
    batchId: Long,
    sourceRowNumber: Long,
    sourceEntityKey: String?,
    values: List<String>,
    projection: Map<String, Int>?
) {
    val p = projection.orEmpty()
    database.execSQL(
        """
        INSERT INTO registry_ra_snapshot(
            batch_id, source_row_number, source_entity_key, nazwa_apteki, stan_apteki, wojewodztwo, powiat, gmina, typ_ulicy, nazwa_ulicy,
            numer_budynku, numer_lokalu, miejscowosc, kod_pocztowy, telefon, fax, email, adres_www, czy_sprzedaz_wysylkowa,
            godziny_otwarcia_poniedzialek, godziny_otwarcia_wtorek, godziny_otwarcia_sroda, godziny_otwarcia_czwartek,
            godziny_otwarcia_piatek, godziny_otwarcia_sobota, godziny_otwarcia_niedziela_handlowa, godziny_otwarcia_niedziela_niehandlowa
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        arrayOf<Any?>(
            batchId, sourceRowNumber, sourceEntityKey,
            values.getOrNull(p["nazwa_apteki"] ?: -1),
            values.getOrNull(p["stan_apteki"] ?: -1),
            values.getOrNull(p["wojewodztwo"] ?: -1),
            values.getOrNull(p["powiat"] ?: -1),
            values.getOrNull(p["gmina"] ?: -1),
            values.getOrNull(p["typ_ulicy"] ?: -1),
            values.getOrNull(p["nazwa_ulicy"] ?: -1),
            values.getOrNull(p["numer_budynku"] ?: -1),
            values.getOrNull(p["numer_lokalu"] ?: -1),
            values.getOrNull(p["miejscowosc"] ?: -1),
            values.getOrNull(p["kod_pocztowy"] ?: -1),
            values.getOrNull(p["telefon"] ?: -1),
            values.getOrNull(p["fax"] ?: -1),
            values.getOrNull(p["email"] ?: -1),
            values.getOrNull(p["adres_www"] ?: -1),
            values.getOrNull(p["czy_sprzedaz_wysylkowa"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_poniedzialek"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_wtorek"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_sroda"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_czwartek"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_piatek"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_sobota"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_niedziela_handlowa"] ?: -1),
            values.getOrNull(p["godziny_otwarcia_niedziela_niehandlowa"] ?: -1)
        )
    )
}


private data class RegistryTables(val rowTable: String, val cellTable: String, val columnTable: String)

private fun tablesFor(source: RegistryFileSource): RegistryTables = when (source) {
    RegistryFileSource.RPL_CSV, RegistryFileSource.RPL_XLSX -> RegistryTables("registry_rpl_row", "registry_rpl_cell", "registry_rpl_column_dictionary")
    RegistryFileSource.RA_CSV, RegistryFileSource.RA_XLS -> RegistryTables("registry_ra_row", "registry_ra_cell", "registry_ra_column_dictionary")
    RegistryFileSource.RDG_XML -> RegistryTables("registry_rdg_row", "registry_rdg_cell", "registry_rdg_column_dictionary")
}
