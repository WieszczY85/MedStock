package pl.syntaxdevteam.medstock.core.reminders

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ReminderDoseEvent(
    val id: Long,
    val reminderId: Long,
    val action: String,
    val medicationSummary: String,
    val occurredAtUtc: String,
) {
    fun displayTimestamp(): String = runCatching {
        LocalDateTime.parse(occurredAtUtc, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault(occurredAtUtc)

    companion object {
        const val ACTION_TAKEN = "taken"
        const val ACTION_SNOOZED = "snoozed"
        const val ACTION_SKIPPED = "skipped"
    }
}
