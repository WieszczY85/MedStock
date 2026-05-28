package pl.syntaxdevteam.medstock.core.download

import android.database.sqlite.SQLiteDatabase
import android.util.Log

class RegistrySnapshotPersistence(
    private val database: SQLiteDatabase,
    private val parserVersion: String = "1",
    private val snapshotRetentionCount: Int = DEFAULT_SNAPSHOT_RETENTION_COUNT
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
        require(snapshotDateUtc.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))

        database.beginTransaction()
        try {
            when (source) {
                RegistryFileSource.RPL_CSV, RegistryFileSource.RPL_XLSX -> {
                    database.delete("rpl", "data_snapshot = ?", arrayOf(snapshotDateUtc))
                    val projection = prepareProjection(resolveHeaders(parsed.headers).map(::canonicalizeHeader), rplHeaderMap)
                    var savedRows = 0
                    parsed.records.forEachIndexed { index, r ->
                        insertRpl(snapshotDateUtc, index.toLong(), r.values, projection)
                        savedRows++
                    }
                    pruneSnapshots(table = "rpl", keepLast = snapshotRetentionCount)
                    logSavedRows(source, snapshotDateUtc, savedRows)
                }
                RegistryFileSource.RA_CSV, RegistryFileSource.RA_XLS -> {
                    database.delete("ra", "data_snapshot = ?", arrayOf(snapshotDateUtc))
                    val projection = prepareProjection(resolveHeaders(parsed.headers).map(::canonicalizeHeader), raHeaderMap)
                    var savedRows = 0
                    parsed.records.forEachIndexed { index, r ->
                        insertRa(snapshotDateUtc, index.toLong(), detectEntityKey(source, parsed.headers, r.values), r.values, projection)
                        savedRows++
                    }
                    pruneSnapshots(table = "ra", keepLast = snapshotRetentionCount)
                    logSavedRows(source, snapshotDateUtc, savedRows)
                }
                RegistryFileSource.RDG_XML -> {
                    database.delete("rdg", "data_snapshot = ?", arrayOf(snapshotDateUtc))
                    val headers = resolveHeaders(parsed.headers)
                    var savedRows = 0
                    parsed.records.forEach { r ->
                        database.execSQL(
                            "INSERT OR REPLACE INTO rdg(data_snapshot,source_row_number,source_entity_key,raw_payload) VALUES(?,?,?,?)",
                            arrayOf<Any?>(
                                snapshotDateUtc,
                                r.rowNumber,
                                detectEntityKey(source, parsed.headers, r.values),
                                serializeRecordPayload(headers, r.values)
                            )
                        )
                        savedRows++
                    }
                    pruneSnapshots(table = "rdg", keepLast = snapshotRetentionCount)
                    logSavedRows(source, snapshotDateUtc, savedRows)
                }
            }
            database.setTransactionSuccessful()
            return 1L
        } finally {
            database.endTransaction()
        }
    }

    private fun pruneSnapshots(table: String, keepLast: Int) {
        database.execSQL(
            """
            DELETE FROM $table
            WHERE data_snapshot NOT IN (
                SELECT data_snapshot FROM $table GROUP BY data_snapshot ORDER BY data_snapshot DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf(keepLast)
        )
    }

    private fun resolveHeaders(headers: List<String>): List<String> = headers.mapIndexed { i, h -> h.trim().ifBlank { "kolumna_${i + 1}" } }

    private fun detectEntityKey(source: RegistryFileSource, headers: List<String>, values: List<String>): String? {
        val preferredHeader = when (source) {
            RegistryFileSource.RA_CSV, RegistryFileSource.RA_XLS -> "identyfikator_apteki"
            RegistryFileSource.RPL_CSV, RegistryFileSource.RPL_XLSX -> "Identyfikator Produktu Leczniczego"
            RegistryFileSource.RDG_XML -> "NumerDecyzji"
        }
        val index = headers.indexOfFirst { it.trim().equals(preferredHeader, true) }
        return values.getOrNull(index)?.takeIf { it.isNotBlank() }
    }

    private fun insertRpl(snapshot: String, sourceRowNumber: Long, values: List<String>, p: Map<String, Int>) {
        val productId = values.getOrNull(p["identyfikator_produktu_leczniczego"] ?: -1).orEmpty().ifBlank { "row_$sourceRowNumber" }
        database.execSQL(
            """
            INSERT OR REPLACE INTO rpl(
                data_snapshot,identyfikator_produktu,nazwa_produktu,droga_podania,moc,postac_farmaceutyczna,
                podmiot_odpowiedzialny,kod_ean,opakowanie,substancja_czynna,kraj_wytworcy,ulotka,charakterystyka
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                snapshot,
                productId,
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

    private fun insertRa(snapshot: String, sourceRowNumber: Long, entityKey: String?, values: List<String>, p: Map<String, Int>) {
        val safeEntityKey = entityKey?.takeIf { it.isNotBlank() } ?: "row_$sourceRowNumber"
        database.execSQL(
            """
            INSERT OR REPLACE INTO ra(
                data_snapshot,source_entity_key,nazwa_apteki,stan_apteki,wojewodztwo,powiat,gmina,typ_ulicy,nazwa_ulicy,
                numer_budynku,numer_lokalu,miejscowosc,kod_pocztowy,telefon,fax,email,adres_www,czy_sprzedaz_wysylkowa,
                godziny_otwarcia_poniedzialek,godziny_otwarcia_wtorek,godziny_otwarcia_sroda,godziny_otwarcia_czwartek,
                godziny_otwarcia_piatek,godziny_otwarcia_sobota,godziny_otwarcia_niedziela_handlowa,godziny_otwarcia_niedziela_niehandlowa
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                snapshot, safeEntityKey,
                values.getOrNull(p["nazwa_apteki"] ?: -1), values.getOrNull(p["stan_apteki"] ?: -1), values.getOrNull(p["wojewodztwo"] ?: -1),
                values.getOrNull(p["powiat"] ?: -1), values.getOrNull(p["gmina"] ?: -1), values.getOrNull(p["typ_ulicy"] ?: -1),
                values.getOrNull(p["nazwa_ulicy"] ?: -1), values.getOrNull(p["numer_budynku"] ?: -1), values.getOrNull(p["numer_lokalu"] ?: -1),
                values.getOrNull(p["miejscowosc"] ?: -1), values.getOrNull(p["kod_pocztowy"] ?: -1), values.getOrNull(p["telefon"] ?: -1),
                values.getOrNull(p["fax"] ?: -1), values.getOrNull(p["email"] ?: -1), values.getOrNull(p["adres_www"] ?: -1),
                values.getOrNull(p["czy_sprzedaz_wysylkowa"] ?: -1), values.getOrNull(p["godziny_otwarcia_poniedzialek"] ?: -1),
                values.getOrNull(p["godziny_otwarcia_wtorek"] ?: -1), values.getOrNull(p["godziny_otwarcia_sroda"] ?: -1),
                values.getOrNull(p["godziny_otwarcia_czwartek"] ?: -1), values.getOrNull(p["godziny_otwarcia_piatek"] ?: -1),
                values.getOrNull(p["godziny_otwarcia_sobota"] ?: -1), values.getOrNull(p["godziny_otwarcia_niedziela_handlowa"] ?: -1),
                values.getOrNull(p["godziny_otwarcia_niedziela_niehandlowa"] ?: -1)
            )
        )
    }

    private companion object {
        const val DEFAULT_SNAPSHOT_RETENTION_COUNT = 14
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
            "nazwa_apteki" to listOf("nazwa_apteki"), "stan_apteki" to listOf("stan_apteki", "status_apteki"), "wojewodztwo" to listOf("wojewodztwo"),
            "powiat" to listOf("powiat"), "gmina" to listOf("gmina"), "typ_ulicy" to listOf("typ_ulicy"), "nazwa_ulicy" to listOf("nazwa_ulicy"),
            "numer_budynku" to listOf("numer_budynku"), "numer_lokalu" to listOf("numer_lokalu"), "miejscowosc" to listOf("miejscowosc"),
            "kod_pocztowy" to listOf("kod_pocztowy"), "telefon" to listOf("telefon"), "fax" to listOf("fax"), "email" to listOf("email", "e_mail"),
            "adres_www" to listOf("adres_www", "www"), "czy_sprzedaz_wysylkowa" to listOf("czy_sprzedaz_wysylkowa", "sprzedaz_wysylkowa"),
            "godziny_otwarcia_poniedzialek" to listOf("godziny_otwarcia_poniedzialek"), "godziny_otwarcia_wtorek" to listOf("godziny_otwarcia_wtorek"),
            "godziny_otwarcia_sroda" to listOf("godziny_otwarcia_sroda"), "godziny_otwarcia_czwartek" to listOf("godziny_otwarcia_czwartek"),
            "godziny_otwarcia_piatek" to listOf("godziny_otwarcia_piatek"), "godziny_otwarcia_sobota" to listOf("godziny_otwarcia_sobota"),
            "godziny_otwarcia_niedziela_handlowa" to listOf("godziny_otwarcia_niedziela_handlowa"), "godziny_otwarcia_niedziela_niehandlowa" to listOf("godziny_otwarcia_niedziela_niehandlowa")
        )
    }
}

private fun logSavedRows(source: RegistryFileSource, snapshotDateUtc: String, savedRows: Int) {
    Log.i("RegistryPersistence", "Saved source=${source.name} snapshot=$snapshotDateUtc rows=$savedRows")
}

internal fun canonicalizeHeader(input: String): String = input
    .lowercase()
    .replace("ł", "l").replace("ą", "a").replace("ć", "c").replace("ę", "e")
    .replace("ń", "n").replace("ó", "o").replace("ś", "s").replace("ż", "z").replace("ź", "z")
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')

internal fun prepareProjection(canonicalHeaders: List<String>, mapping: Map<String, List<String>>): Map<String, Int> =
    mapping.mapNotNull { (target, aliases) ->
        canonicalHeaders.indexOfFirst { it in aliases }.takeIf { it >= 0 }?.let { target to it }
    }.toMap()

internal fun serializeRecordPayload(headers: List<String>, values: List<String>): String {
    val pairs = headers.mapIndexed { index, header ->
        val key = if (header.isBlank()) "kolumna_${index + 1}" else header
        "\"${escapeJson(key)}\":\"${escapeJson(values.getOrNull(index).orEmpty())}\""
    }
    return "{${pairs.joinToString(",")}}"
}

private fun escapeJson(input: String): String = buildString(input.length) {
    input.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}
