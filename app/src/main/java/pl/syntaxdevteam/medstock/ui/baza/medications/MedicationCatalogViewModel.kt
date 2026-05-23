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
    val ean: String,
    val packageSize: String
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

    private val pageSize = 20
    private var snapshotDate: String? = null
    private var snapshotBatchId: Long? = null
    private var recordCount: Int = 0
    private var offset: Int = 0
    private var selectedLetter: String = "#"
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
                    "#" -> args += "[A-Z0-9a-z]*"
                    else -> args += "$selectedLetter%"
                }
                args += pageSize.toString()
                args += offset.toString()

                db.rawQuery(
                """
                SELECT s.source_entity_key,
                       COALESCE(s.nazwa_produktu_leczniczego, ''),
                       COALESCE(s.substancja_czynna, ''),
                       COALESCE(s.moc, ''),
                       COALESCE(s.kod_ean, ''),
                       COALESCE(s.opakowanie, '')
                FROM registry_rpl_snapshot s
                WHERE s.batch_id = ?
                  $clause
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
                        ean = cursor.getString(4).orEmpty(),
                        packageSize = cursor.getString(5).orEmpty()
                    )
                } while (cursor.moveToNext())

                loadedItems += entries
                offset += entries.size
                val canLoadMore = entries.size == pageSize

                _uiState.postValue(
                    MedicationCatalogUiState(
                        summaryResId = R.string.medication_catalog_summary,
                        summaryArgs = listOf(recordCount, selectedSnapshotDate, loadedItems.size),
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
            "#" -> "AND COALESCE(s.nazwa_produktu_leczniczego, '') NOT GLOB ?"
            else -> "AND UPPER(COALESCE(s.nazwa_produktu_leczniczego, '')) LIKE ?"
        }
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

    private fun scalarInt(db: android.database.sqlite.SQLiteDatabase, sql: String): Int {
        db.rawQuery(sql, emptyArray()).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}
