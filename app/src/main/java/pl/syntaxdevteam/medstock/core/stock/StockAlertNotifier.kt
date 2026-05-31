package pl.syntaxdevteam.medstock.core.stock

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.syntaxdevteam.medstock.MainActivity
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.ui.medicationlist.MedicationUnitFormatter
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication

class StockAlertNotifier(private val context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun showCurrentStockAlerts(medications: List<UserMedication>) {
        if (!canPostNotifications()) return
        ensureChannel()
        medications.forEach { medication ->
            val stockInfo = MedicationStockCalculator.calculate(medication)
            if (stockInfo.status == MedicationStockStatus.OK) {
                clearNotificationState(medication.id)
                return@forEach
            }
            if (hasAlreadyNotified(medication.id, stockInfo.status)) return@forEach
            showNotification(medication, stockInfo)
            markNotified(medication.id, stockInfo.status)
        }
    }

    fun clearNotificationState(medicationId: Long) {
        preferences.edit()
            .remove(key(medicationId, MedicationStockStatus.LOW))
            .remove(key(medicationId, MedicationStockStatus.EMPTY))
            .apply()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(medication: UserMedication, stockInfo: MedicationStockInfo) {
        val unit = MedicationUnitFormatter.abbreviate(medication.unit).ifBlank {
            context.getString(R.string.medication_default_unit)
        }
        val title = when (stockInfo.status) {
            MedicationStockStatus.LOW -> context.getString(R.string.stock_alert_notification_low_title)
            MedicationStockStatus.EMPTY -> context.getString(R.string.stock_alert_notification_empty_title)
            MedicationStockStatus.OK -> return
        }
        val text = context.getString(
            R.string.stock_alert_notification_message,
            medication.name,
            medication.currentStock,
            unit,
            stockInfo.daysSupply
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            medication.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify((NOTIFICATION_BASE_ID + medication.id).toInt(), notification)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stock_alert_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.stock_alert_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasAlreadyNotified(medicationId: Long, status: MedicationStockStatus): Boolean {
        return preferences.getBoolean(key(medicationId, status), false)
    }

    private fun markNotified(medicationId: Long, status: MedicationStockStatus) {
        preferences.edit().putBoolean(key(medicationId, status), true).apply()
    }

    private fun key(medicationId: Long, status: MedicationStockStatus): String {
        return "stock_notification_${medicationId}_${status.name}"
    }

    private companion object {
        const val CHANNEL_ID = "stock_alerts"
        const val NOTIFICATION_BASE_ID = 40_000
        const val PREFERENCES_NAME = "stock_alert_notifications"
    }
}
