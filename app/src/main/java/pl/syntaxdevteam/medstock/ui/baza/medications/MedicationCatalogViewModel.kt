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
import pl.syntaxdevteam.medstock.core.barcode.PackageCodeNormalizer
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper

data class MedicationCatalogEntry(
    val entityKey: String,
    val displayName: String,
    val commonName: String,
    val dose: String,
    val route: String,
    val pharmaceuticalForm: String,
    val responsibleEntity: String,
    val manufacturerCountry: String,
    val packages: List<MedicationPackageInfo>,
    val leafletUrl: String,
    val characteristicsUrl: String
) {
    val primaryAvailabilityCode: String = packages
        .firstNotNullOfOrNull { it.availabilityCode.takeIf(String::isNotBlank) }
        .orEmpty()
}

data class MedicationPackageInfo(
    val ean: String,
    val availabilityCode: String,
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
    private var recordCount: Int = 0
    private var offset: Int = 0
    private var selectedLetter: String = "#"
    private var searchQuery: String = ""
    private var searchMode: SearchMode = SearchMode.NAME
    @Volatile private var isPageLoading: Boolean = false
    @Volatile private var pendingReset: Boolean = false
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
        if (normalized == searchQuery && searchMode != SearchMode.PACKAGE_CODE) return
        searchQuery = normalized
        searchMode = SearchMode.NAME
        reloadCatalog()
    }

    fun onPackageCodeSearchRequested(rawCode: String) {
        val normalizedCode = PackageCodeNormalizer.normalize(rawCode)
        if (normalizedCode.isBlank()) return
        selectedLetter = "#"
        searchQuery = normalizedCode
        searchMode = SearchMode.PACKAGE_CODE
        reloadCatalog()
    }

    private fun reloadCatalog() {
        offset = 0
        snapshotDate = null
        loadedItems.clear()
        _uiState.postValue(MedicationCatalogUiState(summaryResId = R.string.medication_catalog_loading, selectedLetter = selectedLetter))
        loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isPageLoading) {
                if (reset) pendingReset = true
                return@launch
            }
            isPageLoading = true
            try {
                val db = RegistryIngestDatabaseHelper.getInstance(getApplication()).readableDatabase
                val diagnostics = readDiagnostics(db)
                Log.i(tag, "DB diagnostics: $diagnostics")

                if (snapshotDate == null || reset) {
                    db.rawQuery(
                    """
                    SELECT data_snapshot
                    FROM rpl
                    ORDER BY data_snapshot DESC
                    LIMIT 1
                    """.trimIndent(),
                    emptyArray()
                    ).use { snapshotCursor ->
                        if (!snapshotCursor.moveToFirst()) {
                            _uiState.postValue(MedicationCatalogUiState(summaryResId = R.string.medication_catalog_empty, selectedLetter = selectedLetter))
                            return@launch
                        }
                        snapshotDate = snapshotCursor.getString(0)
                        recordCount = scalarInt(
                            db,
                            "SELECT COUNT(*) FROM rpl WHERE data_snapshot = ?",
                            arrayOf(snapshotDate ?: "")
                        )
                    }
                }

                val selectedSnapshotDate = snapshotDate ?: return@launch
                val clause = buildFilterClause(selectedLetter)
                val args = mutableListOf<String>(selectedSnapshotDate)
                when (selectedLetter) {
                    "123" -> args += "[0-9]*"
                    "#" -> Unit
                    else -> args += "$selectedLetter%"
                }
                if (searchQuery.isNotBlank() && reset && searchMode != SearchMode.PACKAGE_CODE) {
                    searchMode = resolveSearchMode(db, selectedSnapshotDate, selectedLetter, searchQuery)
                }
                val searchClause = buildSearchClause(searchQuery, searchMode)
                if (searchQuery.isNotBlank()) {
                    args += buildSearchPatterns(searchQuery, searchMode)
                }
                args += pageSize.toString()
                args += offset.toString()

                db.rawQuery(
                """
                SELECT COALESCE(s.identyfikator_produktu, ''),
                       COALESCE(s.nazwa_produktu, ''),
                       COALESCE(s.substancja_czynna, ''),
                       COALESCE(s.moc, ''),
                       COALESCE(s.droga_podania, ''),
                       COALESCE(s.postac_farmaceutyczna, ''),
                       COALESCE(s.podmiot_odpowiedzialny, ''),
                       COALESCE(s.kraj_wytworcy, ''),
                       COALESCE(s.opakowanie, ''),
                       COALESCE(s.ulotka, ''),
                       COALESCE(s.charakterystyka, '')
                FROM rpl s
                WHERE s.data_snapshot = ?
                  $clause
                  $searchClause
                ORDER BY s.nazwa_produktu COLLATE NOCASE ASC
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
                        pharmaceuticalForm = cursor.getString(5).orEmpty(),
                        responsibleEntity = cursor.getString(6).orEmpty(),
                        manufacturerCountry = cursor.getString(7).orEmpty(),
                        packages = parsePackageInfo(
                            kodEan = "",
                            opakowanie = cursor.getString(8).orEmpty()
                        ),
                        leafletUrl = cursor.getString(9).orEmpty(),
                        characteristicsUrl = cursor.getString(10).orEmpty()
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
                Log.i(tag, "Załadowano listę leków: batch=${entries.size}, totalLoaded=${loadedItems.size}, selectedLetter=$selectedLetter, snapshotDate=$selectedSnapshotDate, recordCount=$recordCount")
                }
            } finally {
                isPageLoading = false
                if (pendingReset) {
                    pendingReset = false
                    loadPage(reset = true)
                }
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
            "123" -> "AND COALESCE(s.nazwa_produktu, '') GLOB ?"
            "#" -> ""
            else -> "AND UPPER(COALESCE(s.nazwa_produktu, '')) LIKE ?"
        }
    }

    private fun buildSearchClause(query: String, mode: SearchMode): String {
        if (query.isBlank()) return ""
        if (mode == SearchMode.PACKAGE_CODE) {
            val placeholders = PackageCodeNormalizer.lookupVariants(query)
                .joinToString(" OR ") { "COALESCE(s.opakowanie, '') LIKE ?" }
            return if (placeholders.isBlank()) "" else "AND ($placeholders)"
        }
        val targetColumn = when (mode) {
            SearchMode.NAME -> "nazwa_produktu"
            SearchMode.SUBSTANCE -> "substancja_czynna"
            SearchMode.PACKAGE_CODE -> error("Package code search is handled before column mode resolution.")
        }
        return """
            AND (
                COALESCE(s.$targetColumn, '') LIKE ?
                OR COALESCE(s.$targetColumn, '') LIKE ?
                OR COALESCE(s.$targetColumn, '') LIKE ?
            )
        """.trimIndent()
    }

    private fun buildSearchPatterns(query: String, mode: SearchMode): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        if (mode == SearchMode.PACKAGE_CODE) {
            return PackageCodeNormalizer.lookupVariants(trimmed).map { "%$it%" }
        }
        return listOf(
            "%$trimmed%",
            "%${trimmed.lowercase(polishLocale)}%",
            "%${trimmed.uppercase(polishLocale)}%"
        )
    }

    private fun resolveSearchMode(
        db: android.database.sqlite.SQLiteDatabase,
        snapshotDate: String,
        letterFilter: String,
        query: String
    ): SearchMode {
        val letterClause = buildFilterClause(letterFilter)
        val args = mutableListOf<String>(snapshotDate)
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
            FROM rpl s
            WHERE s.data_snapshot = ?
              $letterClause
              AND UPPER(COALESCE(s.nazwa_produktu, '')) LIKE ?
            """.trimIndent(),
            args.toTypedArray()
        )
        return if (count > 0) SearchMode.NAME else SearchMode.SUBSTANCE
    }

    private enum class SearchMode {
        NAME,
        SUBSTANCE,
        PACKAGE_CODE
    }

    private fun readDiagnostics(db: android.database.sqlite.SQLiteDatabase): String {
        val totalSnapshots = scalarInt(db, "SELECT COUNT(DISTINCT data_snapshot) FROM rpl")
        val totalRows = scalarInt(db, "SELECT COUNT(*) FROM rpl") +
            scalarInt(db, "SELECT COUNT(*) FROM ra") +
            scalarInt(db, "SELECT COUNT(*) FROM rdg")
        val rplBatches = scalarInt(db, "SELECT COUNT(DISTINCT data_snapshot) FROM rpl")
        val rplRows = scalarInt(
            db,
            """
            SELECT COUNT(*)
            FROM rpl r
            """.trimIndent()
        )
        return "snapshots=$totalSnapshots rows=$totalRows rplSnapshots=$rplBatches rplRows=$rplRows"
    }

    private fun scalarInt(db: android.database.sqlite.SQLiteDatabase, sql: String, args: Array<String> = emptyArray()): Int {
        db.rawQuery(sql, args).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun parsePackageInfo(kodEan: String, opakowanie: String): List<MedicationPackageInfo> {
        return MedicationPackageParser.parse(
            kodEan = kodEan,
            opakowanie = opakowanie,
            unknownPackageLabel = getApplication<Application>().getString(R.string.medication_catalog_package_unknown)
        )
    }
}

internal object MedicationPackageParser {
    private val eanRegex = Regex("\\d{8,14}")
    private val strictEanLineRegex = Regex("^\\s*(\\d{8,14})\\s*(?:¦.*)?$")
    private val availabilityCodeRegex = Regex("(?i)^(OTC|Rpz|Rpw|Rp|Lz)$")
    private const val packageUnitPattern = "tabl\\.?|tabletki|kaps\\.?|kapsuł\\w*|amp\\.?|fiol\\.?|saszet\\w*|ml|g|mg|szt\\.?|j\\.?|jedn\\w*|op\\.?|opak\\w*|but\\.?|butel\\w*|flak\\w*|czop\\w*|glob\\w*|draż\\w*"
    private val packageUnitsRegex = Regex("(?i)($packageUnitPattern)")
    private val quantityOnlyRegex = Regex(
        "(\\d+[\\d\\s,./xX-]*(?:$packageUnitPattern)[^¦]*)",
        RegexOption.IGNORE_CASE
    )

    fun parse(kodEan: String, opakowanie: String, unknownPackageLabel: String): List<MedicationPackageInfo> {
        val normalizedPackaging = opakowanie
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r\n", "\n")
        val lines = normalizedPackaging.lines().map { it.trim() }.filter { it.isNotBlank() }
        val packages = mutableListOf<MedicationPackageInfo>()
        val compactInput = normalizedPackaging.replace("\n", " ")
        if (lines.size == 1 && eanRegex.findAll(compactInput).count() > 1) {
            // Dla jednowierszowych danych z wieloma EAN zostawiamy parsowanie fallbackowi segmentowemu.
        } else {
            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                val eanFromLine = strictEanLineRegex.find(line)?.groupValues?.get(1)
                if (!eanFromLine.isNullOrBlank()) {
                    val inlineQuantity = extractInlineQuantity(line)
                    if (inlineQuantity != null) {
                        packages += MedicationPackageInfo(
                            ean = eanFromLine,
                            availabilityCode = extractAvailabilityCode(line),
                            quantity = inlineQuantity
                        )
                        index++
                        continue
                    }
                    val nextLine = lines.getOrNull(index + 1)
                    if (!nextLine.isNullOrBlank()) {
                        val quantityCandidate = nextLine.substringBefore("¦").trim()
                        if (isPlausibleQuantity(quantityCandidate)) {
                            packages += MedicationPackageInfo(
                                ean = eanFromLine,
                                availabilityCode = extractAvailabilityCode(line),
                                quantity = quantityCandidate
                            )
                            index += 2
                            continue
                        }
                    }
                }
                index++
            }
        }
        if (packages.isEmpty() && normalizedPackaging.isNotBlank()) {
            val compact = compactInput
            val eans = eanRegex.findAll(compact).toList()
            for (index in eans.indices) {
                val ean = eans[index].value
                val segmentStart = eans[index].range.last + 1
                val segmentEnd = if (index + 1 < eans.size) eans[index + 1].range.first else compact.length
                val segment = compact.substring(segmentStart, segmentEnd)
                val quantity = quantityOnlyRegex.find(segment)?.groupValues?.get(1)?.trim()
                if (!quantity.isNullOrBlank()) {
                    packages += MedicationPackageInfo(
                        ean = ean,
                        availabilityCode = extractAvailabilityCode(segment),
                        quantity = quantity
                    )
                }
            }
        }
        if (packages.isEmpty() && normalizedPackaging.isNotBlank()) {
            val compact = normalizedPackaging.replace("\n", " ").trim()
            val hasAnyEan = eanRegex.containsMatchIn(compact)
            if (!hasAnyEan) {
                val quantity = quantityOnlyRegex.find(compact)?.groupValues?.get(1)?.trim().orEmpty()
                val fallbackQuantity = when {
                    quantity.isNotBlank() -> quantity
                    compact.contains(Regex("[A-Za-zĄąĆćĘęŁłŃńÓóŚśŹźŻż]")) -> compact
                    else -> ""
                }
                if (fallbackQuantity.isNotBlank()) {
                    return listOf(MedicationPackageInfo(ean = "-", availabilityCode = "", quantity = fallbackQuantity))
                }
            }
        }
        if (packages.isEmpty() && kodEan.isNotBlank()) {
            return listOf(MedicationPackageInfo(ean = kodEan.trim(), availabilityCode = "", quantity = unknownPackageLabel))
        }
        return packages
    }

    private fun extractAvailabilityCode(value: String): String {
        return value.split("¦")
            .asSequence()
            .map { it.trim() }
            .mapNotNull { availabilityCodeRegex.find(it)?.value }
            .firstOrNull()
            .orEmpty()
    }

    private fun extractInlineQuantity(line: String): String? {
        val segments = line.split("¦").map { it.trim() }.filter { it.isNotBlank() }
        val candidates = segments
            .drop(1)
            .filter { candidate -> !candidate.contains(eanRegex) }
            .filter { isPlausibleQuantity(it) }
        return candidates.firstOrNull { packageUnitsRegex.containsMatchIn(it) } ?: candidates.firstOrNull()
    }

    private fun isPlausibleQuantity(value: String): Boolean {
        if (!value.contains(Regex("\\d"))) return false
        if (packageUnitsRegex.containsMatchIn(value)) return true
        return value.contains(Regex("(?i)\\b(x|ml|mg|g|l|szt|op|amp|kaps|tabl)\\b"))
    }
}
