package pl.syntaxdevteam.medstock.ui.baza.medications

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper

data class MedicationCatalogEntry(
    val entityKey: String,
    val displayName: String,
    val commonName: String,
    val dose: String,
    val route: String,
    val responsibleEntity: String,
    val packages: List<MedicationPackageInfo>
)

data class MedicationPackageInfo(
    val ean: String,
    val quantity: String
)

data class MedicationCatalogUiState(
    val summaryResId: Int,
    val summaryArgs: List<Any> = emptyList(),
    val medications: List<MedicationCatalogEntry> = emptyList(),
    val selectedLetter: String = "#",
    val canLoadMore: Boolean = false
)

class MedicationCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "MedicationCatalogVM"
    private val polishLocale = Locale.forLanguageTag("pl-PL")

    private val pageSize = 20
    private var snapshotDate: String? = null
    private var snapshotBatchId: Long? = null
    private var recordCount: Int = 0
    private var offset: Int = 0
    private var selectedLetter: String = "#"
    private var searchQuery: String = ""
    private var searchMode: SearchMode = SearchMode.NAME
    @Volatile private var isPageLoading: Boolean = false
    private val loadedItems = mutableListOf<MedicationCatalogEntry>()

    private val _uiState = MutableLiveData(
        MedicationCatalogUiState(summaryResId = R.string.medication_catalog_loading)
    )
    val uiState: LiveData<MedicationCatalogUiState> = _uiState

    init {
        reloadCatalog()
    }

    fun onLetterSelected(letter: String) {
        val normalized = normalizeFilter(letter)
        if (normalized == selectedLetter) return
        selectedLetter = normalized
        reloadCatalog()
    }

    fun loadNextPage() {
        val state = _uiState.value ?: return
        if (!state.canLoadMore || isPageLoading) return
        loadPage(reset = false)
    }

    fun onSearchQueryChanged(query: String) {
        val normalized = query.trim()
        if (normalized == searchQuery) return
        searchQuery = normalized
        searchMode = SearchMode.NAME
        reloadCatalog()
    }

    private fun reloadCatalog() {
        offset = 0
        snapshotBatchId = null
        snapshotDate = null
        loadedItems.clear()
        _uiState.postValue(MedicationCatalogUiState(summaryResId = R.string.medication_catalog_loading, selectedLetter = selectedLetter))
        loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isPageLoading) return@launch
            isPageLoading = true
            try {
                val db = RegistryIngestDatabaseHelper.getInstance(getApplication()).readableDatabase
                val diagnostics = readDiagnostics(db)
                Log.i(tag, "DB diagnostics: $diagnostics")

                if (snapshotDate == null || reset) {
                    db.rawQuery(
                    """
                    SELECT id, snapshot_date_utc
                    FROM registry_import_batch
                    WHERE source_code IN ('RPL_CSV', 'RPL_XLSX')
                    ORDER BY snapshot_date_utc DESC,
                             CASE WHEN source_code = 'RPL_XLSX' THEN 0 ELSE 1 END,
                             id DESC
                    LIMIT 1
                    """.trimIndent(),
                    emptyArray()
                    ).use { snapshotCursor ->
                        if (!snapshotCursor.moveToFirst()) {
                            _uiState.postValue(MedicationCatalogUiState(summaryResId = R.string.medication_catalog_empty, selectedLetter = selectedLetter))
                            return@launch
                        }
                        snapshotBatchId = snapshotCursor.getLong(0)
                        snapshotDate = snapshotCursor.getString(1)
                        recordCount = scalarInt(
                            db,
                            "SELECT COUNT(*) FROM registry_rpl_snapshot WHERE batch_id = ${snapshotBatchId ?: -1L}"
                        )
                    }
                }

                val selectedSnapshotDate = snapshotDate ?: return@launch
                val selectedBatchId = snapshotBatchId ?: return@launch
                val clause = buildFilterClause(selectedLetter)
                val args = mutableListOf<String>(selectedBatchId.toString())
                when (selectedLetter) {
                    "123" -> args += "[0-9]*"
                    "#" -> Unit
                    else -> args += "$selectedLetter%"
                }
                if (searchQuery.isNotBlank() && reset) {
                    searchMode = resolveSearchMode(db, selectedBatchId, selectedLetter, searchQuery)
                }
                val searchClause = buildSearchClause(searchQuery, searchMode)
                if (searchQuery.isNotBlank()) {
                    args += buildSearchPatterns(searchQuery)
                }
                args += pageSize.toString()
                args += offset.toString()

                db.rawQuery(
                """
                SELECT s.source_entity_key,
                       COALESCE(s.nazwa_produktu_leczniczego, ''),
                       COALESCE(s.substancja_czynna, ''),
                       COALESCE(s.moc, ''),
                       COALESCE(s.droga_podania_gatunek_tkanka_okres_karencji, ''),
                       COALESCE(s.podmiot_odpowiedzialny, ''),
                       COALESCE(s.kod_ean, ''),
                       COALESCE(s.opakowanie, '')
                FROM registry_rpl_snapshot s
                WHERE s.batch_id = ?
                  $clause
                  $searchClause
                ORDER BY s.nazwa_produktu_leczniczego COLLATE NOCASE ASC
                LIMIT ? OFFSET ?
                """.trimIndent(),
                args.toTypedArray()
                ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    val emptyState = loadedItems.isEmpty()
                    if (emptyState) {
                        Log.w(tag, "Lista leków pusta dla filtra=$selectedLetter. Diagnostics=$diagnostics")
                        _uiState.postValue(MedicationCatalogUiState(summaryResId = R.string.medication_catalog_empty, selectedLetter = selectedLetter))
                    }
                    return@use
                }

                val entries = mutableListOf<MedicationCatalogEntry>()

                do {
                    entries += MedicationCatalogEntry(
                        entityKey = cursor.getString(0).orEmpty(),
                        displayName = cursor.getString(1).orEmpty(),
                        commonName = cursor.getString(2).orEmpty(),
                        dose = cursor.getString(3).orEmpty(),
                        route = cursor.getString(4).orEmpty(),
                        responsibleEntity = cursor.getString(5).orEmpty(),
                        packages = parsePackageInfo(
                            kodEan = cursor.getString(6).orEmpty(),
                            opakowanie = cursor.getString(7).orEmpty()
                        )
                    )
                } while (cursor.moveToNext())

                loadedItems += entries
                offset += entries.size
                val canLoadMore = entries.size == pageSize

                _uiState.postValue(
                    MedicationCatalogUiState(
                        summaryResId = R.string.medication_catalog_summary,
                        summaryArgs = listOf(recordCount, selectedSnapshotDate),
                        medications = loadedItems.toList(),
                        selectedLetter = selectedLetter,
                        canLoadMore = canLoadMore
                    )
                )
                Log.i(tag, "Załadowano listę leków: batch=${entries.size}, totalLoaded=${loadedItems.size}, selectedLetter=$selectedLetter, snapshotDate=$selectedSnapshotDate, snapshotBatchId=$selectedBatchId, recordCount=$recordCount")
                }
            } finally {
                isPageLoading = false
            }
        }
    }

    private fun normalizeFilter(letter: String): String {
        val normalized = letter.trim().uppercase(Locale.ROOT)
        return when {
            normalized == "123" -> "123"
            normalized.length == 1 && normalized[0] in 'A'..'Z' -> normalized
            else -> "#"
        }
    }

    private fun buildFilterClause(filter: String): String {
        return when (filter) {
            "123" -> "AND COALESCE(s.nazwa_produktu_leczniczego, '') GLOB ?"
            "#" -> ""
            else -> "AND UPPER(COALESCE(s.nazwa_produktu_leczniczego, '')) LIKE ?"
        }
    }

    private fun buildSearchClause(query: String, mode: SearchMode): String {
        if (query.isBlank()) return ""
        val targetColumn = when (mode) {
            SearchMode.NAME -> "nazwa_produktu_leczniczego"
            SearchMode.SUBSTANCE -> "substancja_czynna"
        }
        return """
            AND (
                COALESCE(s.$targetColumn, '') LIKE ?
                OR COALESCE(s.$targetColumn, '') LIKE ?
                OR COALESCE(s.$targetColumn, '') LIKE ?
            )
        """.trimIndent()
    }

    private fun buildSearchPatterns(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        return listOf(
            "%$trimmed%",
            "%${trimmed.lowercase(polishLocale)}%",
            "%${trimmed.uppercase(polishLocale)}%"
        )
    }

    private fun resolveSearchMode(
        db: android.database.sqlite.SQLiteDatabase,
        batchId: Long,
        letterFilter: String,
        query: String
    ): SearchMode {
        val letterClause = buildFilterClause(letterFilter)
        val args = mutableListOf<String>(batchId.toString())
        when (letterFilter) {
            "123" -> args += "[0-9]*"
            "#" -> Unit
            else -> args += "$letterFilter%"
        }
        args += "%${query.uppercase(Locale.ROOT)}%"

        val count = scalarInt(
            db,
            """
            SELECT COUNT(*)
            FROM registry_rpl_snapshot s
            WHERE s.batch_id = ?
              $letterClause
              AND UPPER(COALESCE(s.nazwa_produktu_leczniczego, '')) LIKE ?
            """.trimIndent(),
            args.toTypedArray()
        )
        return if (count > 0) SearchMode.NAME else SearchMode.SUBSTANCE
    }

    private enum class SearchMode {
        NAME,
        SUBSTANCE
    }

    private fun readDiagnostics(db: android.database.sqlite.SQLiteDatabase): String {
        val totalBatches = scalarInt(db, "SELECT COUNT(*) FROM registry_import_batch")
        val totalRows = scalarInt(db, "SELECT COUNT(*) FROM registry_rpl_snapshot") +
            scalarInt(db, "SELECT COUNT(*) FROM registry_ra_snapshot") +
            scalarInt(db, "SELECT COUNT(*) FROM registry_rdg_row")
        val rplBatches = scalarInt(db, "SELECT COUNT(*) FROM registry_import_batch WHERE source_code IN ('RPL_CSV','RPL_XLSX')")
        val rplRows = scalarInt(
            db,
            """
            SELECT COUNT(*)
            FROM registry_rpl_snapshot r
            JOIN registry_import_batch b ON b.id = r.batch_id
            WHERE b.source_code IN ('RPL_CSV','RPL_XLSX')
            """.trimIndent()
        )
        return "batches=$totalBatches rows=$totalRows rplBatches=$rplBatches rplRows=$rplRows"
    }

    private fun scalarInt(db: android.database.sqlite.SQLiteDatabase, sql: String, args: Array<String> = emptyArray()): Int {
        db.rawQuery(sql, args).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun parsePackageInfo(kodEan: String, opakowanie: String): List<MedicationPackageInfo> {
        val normalizedPackaging = opakowanie
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r\n", "\n")
        val lines = normalizedPackaging.lines().map { it.trim() }.filter { it.isNotBlank() }
        val packages = mutableListOf<MedicationPackageInfo>()
        var pendingEan: String? = null
        val eanRegex = Regex("\\d{8,14}")
        val packageUnitsRegex = Regex("(?i)\\b(tabl\\.?|tabletki|kaps\\.?|kapsuł\\w*|amp\\.?|fiol\\.?|saszet\\w*|ml|g|mg|szt\\.?|j\\.?|jedn\\w*)\\b")
        for (line in lines) {
            val eanCandidate = line.substringBefore("¦").trim()
            if (eanCandidate.matches(eanRegex)) {
                pendingEan = eanCandidate
                continue
            }
            val quantityCandidate = line.substringBefore("¦").trim()
            if (pendingEan != null &&
                quantityCandidate.matches(Regex(".*\\d.*")) &&
                packageUnitsRegex.containsMatchIn(quantityCandidate)
            ) {
                packages += MedicationPackageInfo(
                    ean = pendingEan,
                    quantity = quantityCandidate
                )
                pendingEan = null
            }
        }
        if (packages.isEmpty() && normalizedPackaging.isNotBlank()) {
            val compact = normalizedPackaging.replace("\n", " ")
            val matches = Regex(
                "(\\d{8,14})\\s*¦[^\\n]*?(\\d+\\s*(?:tabl\\.?|tabletki|kaps\\.?|kapsuł\\w*|amp\\.?|fiol\\.?|saszet\\w*|ml|g|mg|szt\\.?|j\\.?|jedn\\w*)[^\\n¦]*)",
                RegexOption.IGNORE_CASE
            ).findAll(compact)
            for (match in matches) {
                packages += MedicationPackageInfo(
                    ean = match.groupValues[1].trim(),
                    quantity = match.groupValues[2].trim()
                )
            }
        }
        if (packages.isEmpty() && kodEan.isNotBlank()) {
            return listOf(
                MedicationPackageInfo(
                    ean = kodEan.trim(),
                    quantity = getApplication<Application>().getString(R.string.medication_catalog_package_unknown)
                )
            )
        }
        return packages
    }
}
