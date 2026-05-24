package pl.syntaxdevteam.medstock.ui.baza.pharmacy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper

data class PharmacyCatalogEntry(
    val entityKey: String,
    val name: String,
    val status: String,
    val city: String,
    val street: String,
    val buildingNumber: String
)

data class PharmacyCatalogUiState(
    val summaryResId: Int,
    val summaryArgs: List<Any> = emptyList(),
    val pharmacies: List<PharmacyCatalogEntry> = emptyList(),
    val selectedLetter: String = "#",
    val canLoadMore: Boolean = false
)

class PharmacyCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val pageSize = 20
    private var snapshotDate: String? = null
    private var snapshotBatchId: Long? = null
    private var recordCount: Int = 0
    private var offset: Int = 0
    private var selectedLetter: String = "#"
    private var searchQuery: String = ""
    @Volatile private var isPageLoading: Boolean = false
    private val loadedItems = mutableListOf<PharmacyCatalogEntry>()

    private val _uiState = MutableLiveData(
        PharmacyCatalogUiState(summaryResId = R.string.pharmacy_catalog_loading)
    )
    val uiState: LiveData<PharmacyCatalogUiState> = _uiState

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
        reloadCatalog()
    }

    private fun reloadCatalog() {
        offset = 0
        snapshotBatchId = null
        snapshotDate = null
        loadedItems.clear()
        _uiState.postValue(PharmacyCatalogUiState(summaryResId = R.string.pharmacy_catalog_loading, selectedLetter = selectedLetter))
        loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isPageLoading) return@launch
            isPageLoading = true
            try {
                val db = RegistryIngestDatabaseHelper.getInstance(getApplication()).readableDatabase
                if (snapshotDate == null || reset) {
                    db.rawQuery(
                        """
                        SELECT id, snapshot_date_utc
                        FROM registry_import_batch
                        WHERE source_code IN ('RA_CSV', 'RA_XLS')
                        ORDER BY snapshot_date_utc DESC,
                                 CASE WHEN source_code = 'RA_XLS' THEN 0 ELSE 1 END,
                                 id DESC
                        LIMIT 1
                        """.trimIndent(),
                        emptyArray()
                    ).use { snapshotCursor ->
                        if (!snapshotCursor.moveToFirst()) {
                            _uiState.postValue(PharmacyCatalogUiState(summaryResId = R.string.pharmacy_catalog_empty, selectedLetter = selectedLetter))
                            return@launch
                        }
                        snapshotBatchId = snapshotCursor.getLong(0)
                        snapshotDate = snapshotCursor.getString(1)
                        recordCount = scalarInt(
                            db,
                            "SELECT COUNT(*) FROM registry_ra_snapshot WHERE batch_id = ${snapshotBatchId ?: -1L}"
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

                val searchClause = if (searchQuery.isBlank()) "" else "AND UPPER(COALESCE(s.nazwa_apteki, '')) LIKE ?"
                if (searchQuery.isNotBlank()) args += "%${searchQuery.uppercase(Locale.ROOT)}%"
                args += pageSize.toString()
                args += offset.toString()

                db.rawQuery(
                    """
                    SELECT s.source_entity_key,
                           COALESCE(s.nazwa_apteki, ''),
                           COALESCE(s.stan_apteki, ''),
                           COALESCE(s.miejscowosc, ''),
                           TRIM(COALESCE(s.typ_ulicy, '') || ' ' || COALESCE(s.nazwa_ulicy, '')),
                           COALESCE(s.numer_budynku, '')
                    FROM registry_ra_snapshot s
                    WHERE s.batch_id = ?
                      $clause
                      $searchClause
                    ORDER BY COALESCE(s.miejscowosc, '') COLLATE NOCASE ASC,
                             COALESCE(s.nazwa_ulicy, '') COLLATE NOCASE ASC,
                             COALESCE(s.nazwa_apteki, '') COLLATE NOCASE ASC
                    LIMIT ? OFFSET ?
                    """.trimIndent(),
                    args.toTypedArray()
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        if (loadedItems.isEmpty()) {
                            _uiState.postValue(PharmacyCatalogUiState(summaryResId = R.string.pharmacy_catalog_empty, selectedLetter = selectedLetter))
                        }
                        return@use
                    }

                    val entries = mutableListOf<PharmacyCatalogEntry>()
                    do {
                        entries += PharmacyCatalogEntry(
                            entityKey = cursor.getString(0).orEmpty(),
                            name = cursor.getString(1).orEmpty(),
                            status = cursor.getString(2).orEmpty(),
                            city = cursor.getString(3).orEmpty(),
                            street = cursor.getString(4).orEmpty(),
                            buildingNumber = cursor.getString(5).orEmpty()
                        )
                    } while (cursor.moveToNext())

                    loadedItems += entries
                    offset += entries.size
                    _uiState.postValue(
                        PharmacyCatalogUiState(
                            summaryResId = R.string.pharmacy_catalog_summary,
                            summaryArgs = listOf(recordCount, selectedSnapshotDate),
                            pharmacies = loadedItems.toList(),
                            selectedLetter = selectedLetter,
                            canLoadMore = entries.size == pageSize
                        )
                    )
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

    private fun buildFilterClause(filter: String): String = when (filter) {
        "123" -> "AND COALESCE(s.nazwa_apteki, '') GLOB ?"
        "#" -> ""
        else -> "AND UPPER(COALESCE(s.nazwa_apteki, '')) LIKE ?"
    }

    private fun scalarInt(db: android.database.sqlite.SQLiteDatabase, sql: String): Int {
        db.rawQuery(sql, emptyArray()).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}
