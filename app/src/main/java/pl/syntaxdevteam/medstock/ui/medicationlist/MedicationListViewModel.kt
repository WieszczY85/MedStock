package pl.syntaxdevteam.medstock.ui.medicationlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.core.download.UserMedicationRepository

class MedicationListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserMedicationRepository(getApplication())

    private val _medications = MutableLiveData<List<UserMedication>>()
    val medications: LiveData<List<UserMedication>> = _medications

    init {
        refreshMedications()
    }

    fun refreshMedications() {
        viewModelScope.launch(Dispatchers.IO) {
            _medications.postValue(repository.getAll())
        }
    }

    fun addMedication(
        rawName: String,
        rawStrength: String,
        rawActiveSubstance: String,
        rawPackageSize: String,
        rawUnit: String,
        rawCurrentStock: String,
        rawDosage: String,
        rawAlertDays: String
    ) {
        val normalizedName = rawName.trim()
        val normalizedStrength = rawStrength.trim()
        val normalizedActiveSubstance = rawActiveSubstance.trim()
        val normalizedPackageSize = rawPackageSize.trim()
        val normalizedUnit = rawUnit.trim()
        val normalizedCurrentStock = rawCurrentStock.trim()
        val normalizedDosage = rawDosage.trim()
        val normalizedAlertDays = rawAlertDays.trim()
        val stock = normalizedCurrentStock.toIntOrNull()
        val alert = normalizedAlertDays.toIntOrNull()
        if (normalizedName.isBlank()) {
            return
        }
        if ((normalizedCurrentStock.isNotBlank() && stock == null) || (normalizedAlertDays.isNotBlank() && alert == null)) return

        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(
                name = normalizedName,
                strength = normalizedStrength,
                activeSubstance = normalizedActiveSubstance,
                packageSize = normalizedPackageSize,
                unit = normalizedUnit,
                currentStock = stock ?: 0,
                dosage = normalizedDosage,
                alertDays = alert ?: 0
            )
            refreshMedications()
        }
    }

    fun updateMedication(
        id: Long,
        rawName: String,
        rawStrength: String,
        rawActiveSubstance: String,
        rawPackageSize: String,
        rawUnit: String,
        rawCurrentStock: String,
        rawDosage: String,
        rawAlertDays: String
    ) {
        val normalizedName = rawName.trim()
        val normalizedStrength = rawStrength.trim()
        val normalizedActiveSubstance = rawActiveSubstance.trim()
        val normalizedPackageSize = rawPackageSize.trim()
        val normalizedUnit = rawUnit.trim()
        val normalizedCurrentStock = rawCurrentStock.trim()
        val normalizedDosage = rawDosage.trim()
        val normalizedAlertDays = rawAlertDays.trim()
        val stock = normalizedCurrentStock.toIntOrNull()
        val alert = normalizedAlertDays.toIntOrNull()
        if (normalizedName.isBlank()) {
            return
        }
        if ((normalizedCurrentStock.isNotBlank() && stock == null) || (normalizedAlertDays.isNotBlank() && alert == null)) return

        viewModelScope.launch(Dispatchers.IO) {
            repository.update(
                id = id,
                name = normalizedName,
                strength = normalizedStrength,
                activeSubstance = normalizedActiveSubstance,
                packageSize = normalizedPackageSize,
                unit = normalizedUnit,
                currentStock = stock ?: 0,
                dosage = normalizedDosage,
                alertDays = alert ?: 0
            )
            refreshMedications()
        }
    }

    fun deleteMedication(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
            refreshMedications()
        }
    }
}
