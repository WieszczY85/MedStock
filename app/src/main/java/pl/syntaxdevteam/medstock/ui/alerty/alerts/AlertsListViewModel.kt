package pl.syntaxdevteam.medstock.ui.alerty.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.core.download.UserMedicationRepository
import pl.syntaxdevteam.medstock.core.stock.MedicationStockCalculator
import pl.syntaxdevteam.medstock.core.stock.MedicationStockStatus
import pl.syntaxdevteam.medstock.core.stock.StockAlertDismissalStore
import pl.syntaxdevteam.medstock.ui.medicationlist.MedicationUnitFormatter

class AlertsListViewModel(application: Application) : AndroidViewModel(application) {

    private val medicationRepository = UserMedicationRepository(getApplication())
    private val dismissalStore = StockAlertDismissalStore(getApplication())

    private val _alerts = MutableLiveData<List<StockAlert>>()
    val alerts: LiveData<List<StockAlert>> = _alerts

    init {
        refreshAlerts()
    }

    fun refreshAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val alerts = medicationRepository.getAll()
                .mapNotNull { medication ->
                    val stockInfo = MedicationStockCalculator.calculate(medication)
                    if (stockInfo.status == MedicationStockStatus.OK) return@mapNotNull null
                    if (dismissalStore.isDismissed(medication.id, stockInfo.status)) return@mapNotNull null
                    StockAlert(
                        medicationId = medication.id,
                        medicationName = medication.name,
                        currentStock = medication.currentStock,
                        unit = MedicationUnitFormatter.abbreviate(medication.unit),
                        daysSupply = stockInfo.daysSupply,
                        status = stockInfo.status,
                    )
                }
            _alerts.postValue(alerts)
        }
    }

    fun dismissAlert(alert: StockAlert) {
        viewModelScope.launch(Dispatchers.IO) {
            dismissalStore.dismiss(alert.medicationId, alert.status)
            refreshAlerts()
        }
    }
}
