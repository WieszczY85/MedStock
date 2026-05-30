package pl.syntaxdevteam.medstock.core.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.syntaxdevteam.medstock.MainActivity
import pl.syntaxdevteam.medstock.R

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return
        val repository = MedicationReminderRepository(context)
        val reminder = repository.findById(reminderId) ?: return
        if (!reminder.enabled) return

        ensureChannel(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            val medicationNames = reminder.medications.joinToString { it.name }.ifBlank {
                context.getString(R.string.reminder_notification_no_medications)
            }
            val title = reminder.label.ifBlank { context.getString(R.string.reminder_notification_title) }
            val contentIntent = PendingIntent.getActivity(
                context,
                reminderId.toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm_black_24dp)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.reminder_notification_message, medicationNames))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.reminder_notification_message, medicationNames)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            NotificationManagerCompat.from(context).notify(reminderId.toInt(), notification)
        }

        if (reminder.repeatsOnAnyDay()) {
            ReminderScheduler(context).schedule(reminder)
        } else {
            repository.setEnabled(reminder.id, false)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "medication_reminders"
    }
}
