package pl.syntaxdevteam.medstock.ui.baza.medications

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper

data class MedicationCatalogEntry(
    val entityKey: String,
    val displayName: String,
    val commonName: String
)

data class MedicationCatalogUiState(
    val summaryResId: Int,
    val summaryArgs: List<Any> = emptyList(),
    val medications: List<MedicationCatalogEntry> = emptyList()
)

class MedicationCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "MedicationCatalogVM"

    private val _uiState = MutableLiveData(
        MedicationCatalogUiState(summaryResId = R.string.medication_catalog_loading)
    )
    val uiState: LiveData<MedicationCatalogUiState> = _uiState

    init {
        loadCatalogPreview()
    }

    private fun loadCatalogPreview() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = RegistryIngestDatabaseHelper(getApplication()).readableDatabase
            val diagnostics = readDiagnostics(db)
            Log.i(tag, "DB diagnostics: $diagnostics")
            db.rawQuery(
                """
                SELECT b.snapshot_date_utc,
                       b.record_count,
                       r.source_entity_key,
                       COALESCE(MAX(CASE WHEN c.column_key = 'Nazwa produktu leczniczego' THEN cell.value_text END), ''),
                       COALESCE(MAX(CASE WHEN c.column_key = 'Nazwa powszechnie stosowana' THEN cell.value_text END), '')
                FROM registry_import_batch b
                JOIN registry_row r ON r.batch_id = b.id
                JOIN registry_cell cell ON cell.row_id = r.id
                JOIN registry_column_dictionary c ON c.id = cell.column_id
                WHERE b.source_code IN ('RPL_CSV', 'RPL_XLSX')
                  AND b.snapshot_date_utc = (
                      SELECT MAX(snapshot_date_utc)
                      FROM registry_import_batch
                      WHERE source_code IN ('RPL_CSV', 'RPL_XLSX')
                  )
                GROUP BY b.snapshot_date_utc, b.record_count, r.id, r.source_entity_key
                ORDER BY r.source_row_number ASC
                LIMIT 200
                """.trimIndent(),
                emptyArray()
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    Log.w(tag, "Lista leków pusta dla źródeł RPL_CSV/RPL_XLSX. Diagnostics=$diagnostics")
                    _uiState.postValue(MedicationCatalogUiState(summaryResId = R.string.medication_catalog_empty))
                    return@use
                }

                val snapshotDate = cursor.getString(0)
                val recordCount = cursor.getInt(1)
                val entries = mutableListOf<MedicationCatalogEntry>()

                do {
                    entries += MedicationCatalogEntry(
                        entityKey = cursor.getString(2).orEmpty(),
                        displayName = cursor.getString(3).orEmpty(),
                        commonName = cursor.getString(4).orEmpty()
                    )
                } while (cursor.moveToNext())

                _uiState.postValue(
                    MedicationCatalogUiState(
                        summaryResId = R.string.medication_catalog_summary,
                        summaryArgs = listOf(recordCount, snapshotDate, entries.size),
                        medications = entries
                    )
                )
                Log.i(tag, "Załadowano listę leków: entries=${entries.size}, snapshotDate=$snapshotDate, recordCount=$recordCount")
            }
        }
    }

    private fun readDiagnostics(db: android.database.sqlite.SQLiteDatabase): String {
        val totalBatches = scalarInt(db, "SELECT COUNT(*) FROM registry_import_batch")
        val totalRows = scalarInt(db, "SELECT COUNT(*) FROM registry_row")
        val rplBatches = scalarInt(db, "SELECT COUNT(*) FROM registry_import_batch WHERE source_code IN ('RPL_CSV','RPL_XLSX')")
        val rplRows = scalarInt(
            db,
            """
            SELECT COUNT(*)
            FROM registry_row r
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
