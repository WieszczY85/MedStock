package pl.syntaxdevteam.medstock.ui.baza.medications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.barcode.PackageCodeNormalizer
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper

sealed interface MedicationCatalogDetailUiState {
    data object Loading : MedicationCatalogDetailUiState
    data class Found(
        val scannedCode: String,
        val snapshotDate: String,
        val medication: MedicationCatalogEntry,
        val matchedPackageCodes: List<String>
    ) : MedicationCatalogDetailUiState
    data class NotFound(val scannedCode: String) : MedicationCatalogDetailUiState
    data object EmptyDatabase : MedicationCatalogDetailUiState
}

class MedicationCatalogDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableLiveData<MedicationCatalogDetailUiState>()
    val uiState: LiveData<MedicationCatalogDetailUiState> = _uiState

    fun loadByPackageCode(rawCode: String) {
        val normalizedCode = PackageCodeNormalizer.normalize(rawCode)
        if (normalizedCode.isBlank()) {
            _uiState.value = MedicationCatalogDetailUiState.NotFound(rawCode)
            return
        }
        _uiState.value = MedicationCatalogDetailUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val db = RegistryIngestDatabaseHelper.getInstance(getApplication()).readableDatabase
            val snapshotDate = db.rawQuery(
                """
                SELECT data_snapshot
                FROM rpl
                ORDER BY data_snapshot DESC
                LIMIT 1
                """.trimIndent(),
                emptyArray()
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }

            if (snapshotDate.isBlank()) {
                _uiState.postValue(MedicationCatalogDetailUiState.EmptyDatabase)
                return@launch
            }

            val codeVariants = PackageCodeNormalizer.lookupVariants(normalizedCode)
            if (codeVariants.isEmpty()) {
                _uiState.postValue(MedicationCatalogDetailUiState.NotFound(normalizedCode))
                return@launch
            }

            val packageCodeClause = codeVariants.joinToString(" OR ") {
                "COALESCE(s.opakowanie, '') LIKE ?"
            }
            val args = mutableListOf(snapshotDate)
            args += codeVariants.map { "%$it%" }
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
                  AND ($packageCodeClause)
                ORDER BY s.nazwa_produktu COLLATE NOCASE ASC, s.moc COLLATE NOCASE ASC
                LIMIT 1
                """.trimIndent(),
                args.toTypedArray()
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    _uiState.postValue(MedicationCatalogDetailUiState.NotFound(normalizedCode))
                    return@use
                }
                val packageDescription = cursor.getString(8).orEmpty()
                val packages = MedicationPackageParser.parse(
                    kodEan = "",
                    opakowanie = packageDescription,
                    unknownPackageLabel = getApplication<Application>().getString(R.string.medication_catalog_package_unknown)
                )
                _uiState.postValue(
                    MedicationCatalogDetailUiState.Found(
                        scannedCode = normalizedCode,
                        snapshotDate = snapshotDate,
                        matchedPackageCodes = codeVariants + findPackageEansForMatchedCodes(packageDescription, codeVariants),
                        medication = MedicationCatalogEntry(
                            entityKey = cursor.getString(0).orEmpty(),
                            displayName = cursor.getString(1).orEmpty(),
                            commonName = cursor.getString(2).orEmpty(),
                            dose = cursor.getString(3).orEmpty(),
                            route = cursor.getString(4).orEmpty(),
                            pharmaceuticalForm = cursor.getString(5).orEmpty(),
                            responsibleEntity = cursor.getString(6).orEmpty(),
                            manufacturerCountry = cursor.getString(7).orEmpty(),
                            packages = packages,
                            leafletUrl = cursor.getString(9).orEmpty(),
                            characteristicsUrl = cursor.getString(10).orEmpty()
                        )
                    )
                )
            }
        }
    }

    private fun findPackageEansForMatchedCodes(packageDescription: String, codeVariants: List<String>): List<String> {
        val normalizedPackaging = packageDescription
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r\n", "\n")
        return normalizedPackaging
            .lines()
            .filter { line ->
                val digits = line.filter(Char::isDigit)
                codeVariants.any { variant ->
                    val candidate = variant.filter(Char::isDigit)
                    candidate.isNotBlank() && digits.contains(candidate)
                }
            }
            .flatMap { line -> PACKAGE_CODE_REGEX.findAll(line).map { it.value }.toList() }
            .distinct()
    }

    companion object {
        private val PACKAGE_CODE_REGEX = Regex("""\b\d{8,14}\b""")
    }
}
