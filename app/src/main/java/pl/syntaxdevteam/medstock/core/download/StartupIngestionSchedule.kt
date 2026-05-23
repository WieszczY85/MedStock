package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class StartupIngestionSchedule(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun shouldRunNow(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val businessDate = resolveBusinessDate(now)
        val lastRunDate = preferences.getString(KEY_LAST_RUN_DATE, null)
        return lastRunDate != businessDate.toString()
    }

    fun markRunCompleted(now: LocalDateTime = LocalDateTime.now()) {
        val businessDate = resolveBusinessDate(now)
        preferences.edit().putString(KEY_LAST_RUN_DATE, businessDate.toString()).apply()
    }

    private fun resolveBusinessDate(now: LocalDateTime): LocalDate {
        val resetTime = LocalTime.of(0, 1)
        return if (now.toLocalTime().isBefore(resetTime)) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    private companion object {
        const val PREFERENCES_NAME = "startup_ingestion_flags"
        const val KEY_LAST_RUN_DATE = "last_run_business_date"
    }
}
