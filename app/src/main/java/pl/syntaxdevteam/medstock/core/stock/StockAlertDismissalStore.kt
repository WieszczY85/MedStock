package pl.syntaxdevteam.medstock.core.stock

import android.content.Context

class StockAlertDismissalStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isDismissed(medicationId: Long, status: MedicationStockStatus): Boolean {
        return preferences.getBoolean(key(medicationId, status), false)
    }

    fun dismiss(medicationId: Long, status: MedicationStockStatus) {
        preferences.edit().putBoolean(key(medicationId, status), true).apply()
    }

    fun clear(medicationId: Long) {
        preferences.edit()
            .remove(key(medicationId, MedicationStockStatus.LOW))
            .remove(key(medicationId, MedicationStockStatus.EMPTY))
            .apply()
    }

    private fun key(medicationId: Long, status: MedicationStockStatus): String {
        return "stock_alert_${medicationId}_${status.name}"
    }

    private companion object {
        const val PREFERENCES_NAME = "stock_alert_dismissals"
    }
}
