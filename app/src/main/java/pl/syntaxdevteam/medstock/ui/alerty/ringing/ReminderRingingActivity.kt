package pl.syntaxdevteam.medstock.ui.alerty.ringing

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.reminders.MedicationReminderRepository
import pl.syntaxdevteam.medstock.core.reminders.ReminderAlertPlayer
import pl.syntaxdevteam.medstock.core.reminders.ReminderAlarmReceiver
import pl.syntaxdevteam.medstock.core.reminders.ReminderDoseEvent
import pl.syntaxdevteam.medstock.core.reminders.ReminderScheduler
import pl.syntaxdevteam.medstock.databinding.ActivityReminderRingingBinding
import pl.syntaxdevteam.medstock.ui.alerty.reminders.MedicationReminder

class ReminderRingingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderRingingBinding
    private lateinit var repository: MedicationReminderRepository
    private lateinit var scheduler: ReminderScheduler
    private lateinit var alertPlayer: ReminderAlertPlayer
    private var reminder: MedicationReminder? = null
    private val resolvedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val resolvedId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
            if (resolvedId == reminder?.id) {
                alertPlayer.stop()
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        binding = ActivityReminderRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = MedicationReminderRepository(this)
        scheduler = ReminderScheduler(this)
        alertPlayer = ReminderAlertPlayer(this)

        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        reminder = repository.findById(reminderId)
        val currentReminder = reminder
        if (currentReminder == null) {
            finish()
            return
        }

        bindReminder(currentReminder)
        alertPlayer.start()

        binding.buttonDoseTaken.setOnClickListener {
            repository.addDoseEvent(currentReminder, ReminderDoseEvent.ACTION_TAKEN)
            scheduler.cancelSnooze(currentReminder.id)
            closeAlarm(currentReminder.id)
        }
        binding.buttonSnooze.setOnClickListener {
            repository.addDoseEvent(currentReminder, ReminderDoseEvent.ACTION_SNOOZED)
            scheduler.scheduleSnooze(currentReminder.id)
            closeAlarm(currentReminder.id)
        }
        binding.buttonSkipDose.setOnClickListener {
            repository.addDoseEvent(currentReminder, ReminderDoseEvent.ACTION_SKIPPED)
            scheduler.cancelSnooze(currentReminder.id)
            closeAlarm(currentReminder.id)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            resolvedReceiver,
            IntentFilter(ReminderAlarmReceiver.ACTION_RINGING_RESOLVED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(resolvedReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        alertPlayer.stop()
        super.onDestroy()
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    private fun bindReminder(reminder: MedicationReminder) {
        binding.textRingingTime.text = reminder.timeLabel
        binding.textRingingTitle.text = reminder.label.ifBlank { getString(R.string.reminder_ringing_title) }
        val medications = reminder.medications.joinToString(separator = "\n") { medication ->
            listOf(medication.name, medication.strength, medication.dosage)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
        }.ifBlank { getString(R.string.reminder_notification_no_medications) }
        binding.textRingingMedications.text = medications
        binding.buttonSnooze.text = getString(R.string.reminder_action_snooze_minutes, ReminderScheduler.SNOOZE_MINUTES)
        bindHistory(reminder.id)
    }

    private fun bindHistory(reminderId: Long) {
        val events = repository.getDoseHistory(reminderId)
        binding.containerDoseHistory.removeAllViews()
        binding.textDoseHistoryEmpty.isVisible = events.isEmpty()
        events.forEach { event ->
            val status = when (event.action) {
                ReminderDoseEvent.ACTION_TAKEN -> getString(R.string.reminder_history_taken)
                ReminderDoseEvent.ACTION_SNOOZED -> getString(R.string.reminder_history_snoozed)
                ReminderDoseEvent.ACTION_SKIPPED -> getString(R.string.reminder_history_skipped)
                else -> event.action
            }
            binding.containerDoseHistory.addView(TextView(this).apply {
                text = getString(R.string.reminder_history_row, event.displayTimestamp(), status, event.medicationSummary)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 6, 0, 6)
            })
        }
    }

    private fun closeAlarm(reminderId: Long) {
        alertPlayer.stop()
        getSystemService(NotificationManager::class.java)?.cancel(reminderId.toInt())
        finishAndRemoveTask()
    }

    companion object {
        fun intent(context: Context, reminderId: Long): Intent = Intent(context, ReminderRingingActivity::class.java).apply {
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
}
