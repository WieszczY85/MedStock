package pl.syntaxdevteam.medstock.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import pl.syntaxdevteam.medstock.ui.alerty.reminders.MedicationReminder
import java.util.Calendar

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: MedicationReminder) {
        cancel(reminder.id)
        if (!reminder.enabled) return
        val triggerAtMillis = nextTriggerAtMillis(reminder)
        val intent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_FIRE_REMINDER
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        }
    }

    fun cancel(reminderId: Long) {
        val intent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply { action = ACTION_FIRE_REMINDER },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (intent != null) {
            alarmManager.cancel(intent)
            intent.cancel()
        }
    }

    fun rescheduleAll() {
        MedicationReminderRepository(context).getAll().filter { it.enabled }.forEach(::schedule)
    }

    fun exactAlarmSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            null
        }
    }

    private fun nextTriggerAtMillis(reminder: MedicationReminder): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
        }
        if (!reminder.repeatsOnAnyDay()) {
            if (!candidate.after(now)) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate.timeInMillis
        }
        repeat(DAYS_IN_WEEK + 1) {
            val appDay = appDayIndex(candidate.get(Calendar.DAY_OF_WEEK))
            val enabledForDay = reminder.dayMask and (1 shl appDay) != 0
            if (enabledForDay && candidate.after(now)) return candidate.timeInMillis
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        candidate.add(Calendar.DAY_OF_YEAR, 1)
        return candidate.timeInMillis
    }

    private fun appDayIndex(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        else -> 6
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "pl.syntaxdevteam.medstock.action.FIRE_MEDICATION_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        private const val DAYS_IN_WEEK = 7
    }
}
