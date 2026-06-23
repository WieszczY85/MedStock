package pl.syntaxdevteam.medstock.core.account

import android.app.backup.BackupManager
import android.content.ContentValues
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper
import pl.syntaxdevteam.medstock.core.download.UserMedicationRepository
import pl.syntaxdevteam.medstock.core.i18n.AppLanguageMode
import pl.syntaxdevteam.medstock.core.i18n.LocaleManager
import pl.syntaxdevteam.medstock.core.reminders.MedicationReminderRepository
import pl.syntaxdevteam.medstock.core.theme.AppThemeMode
import pl.syntaxdevteam.medstock.core.theme.AppColorPalette
import pl.syntaxdevteam.medstock.core.theme.ThemeManager
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DriveBackupSnapshotRepository(context: Context) {

    private val appContext = context.applicationContext
    private val medicationRepository = UserMedicationRepository(appContext)
    private val reminderRepository = MedicationReminderRepository(appContext)
    private val dbHelper = RegistryIngestDatabaseHelper.getInstance(appContext)
    private val driveClient = GoogleDriveBackupClient(appContext)

    fun createSnapshotFile(accountEmail: String): File {
        val medications = medicationRepository.getAll()
        val reminders = reminderRepository.getAll()
        val payload = JSONObject().apply {
            put("format", SNAPSHOT_FORMAT)
            put("accountEmail", accountEmail)
            put("createdAtUtc", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("themeMode", ThemeManager.getThemeMode(appContext).preferenceValue)
                put("colorPalette", ThemeManager.getColorPalette(appContext).preferenceValue)
                put("languageMode", LocaleManager.getLanguageMode(appContext).preferenceValue)
            })
            put("medications", JSONArray().apply {
                medications.forEach { medication ->
                    put(JSONObject().apply {
                        put("id", medication.id)
                        put("name", medication.name)
                        put("strength", medication.strength)
                        put("activeSubstance", medication.activeSubstance)
                        put("packageSize", medication.packageSize)
                        put("unit", medication.unit)
                        put("currentStock", medication.currentStock)
                        put("dosage", medication.dosage)
                        put("alertDays", medication.alertDays)
                        put("lastStockUpdateUtc", medication.lastStockUpdateUtc)
                    })
                }
            })
            put("reminders", JSONArray().apply {
                reminders.forEach { reminder ->
                    put(JSONObject().apply {
                        put("id", reminder.id)
                        put("hour", reminder.hour)
                        put("minute", reminder.minute)
                        put("dayMask", reminder.dayMask)
                        put("enabled", reminder.enabled)
                        put("label", reminder.label)
                        put("soundName", reminder.soundName)
                        put("medicationIds", JSONArray().apply {
                            reminder.medicationIds.forEach { medicationId -> put(medicationId) }
                        })
                    })
                }
            })
        }

        val directory = File(appContext.filesDir, BACKUP_DIRECTORY).apply { mkdirs() }
        return File(directory, BACKUP_FILE_NAME).apply {
            writeText(payload.toString(2))
            BackupManager(appContext).dataChanged()
        }
    }

    fun createAndUploadSnapshot(accountEmail: String): BackupSnapshotMetadata {
        val snapshot = createSnapshotFile(accountEmail)
        driveClient.uploadSnapshot(accountEmail, snapshot)
        return readRestorableSnapshotMetadata(snapshot, accountEmail)
            ?: error("Created backup snapshot is not restorable for $accountEmail")
    }

    fun findRestorableSnapshot(accountEmail: String): BackupSnapshotMetadata? {
        val remoteSnapshot = runCatching { driveClient.downloadLatestSnapshot(accountEmail, snapshotFile()) }
            .recoverCatching { throwable ->
                if (throwable is DriveBackupAuthorizationRequiredException) throw throwable
                null
            }
            .getOrThrow()
        if (remoteSnapshot != null) return remoteSnapshot
        return findLocalRestorableSnapshot(accountEmail)
    }

    fun restoreLatestSnapshot(accountEmail: String): BackupRestoreResult {
        val remoteSnapshot = runCatching { driveClient.downloadLatestSnapshot(accountEmail, snapshotFile()) }
            .recoverCatching { throwable ->
                if (throwable is DriveBackupAuthorizationRequiredException) throw throwable
                null
            }
            .getOrThrow()
        if (remoteSnapshot == null) {
            findLocalRestorableSnapshot(accountEmail) ?: error("No restorable backup snapshot for $accountEmail")
        }
        val payload = JSONObject(snapshotFile().readText())
        val medicationIdMap = mutableMapOf<Long, Long>()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("medication_reminder_dose_event", null, null)
            db.delete("medication_reminder_medication", null, null)
            db.delete("medication_reminder", null, null)
            db.delete("user_medication", null, null)

            val medications = payload.optJSONArray("medications") ?: JSONArray()
            val fallbackLastStockUpdateUtc = backupCreatedAtAsDatabaseDateTime(payload)
            for (index in 0 until medications.length()) {
                val medication = medications.optJSONObject(index) ?: continue
                val lastStockUpdateUtc = medication.optString("lastStockUpdateUtc")
                    .takeIf { it.isNotBlank() }
                    ?: fallbackLastStockUpdateUtc
                val snapshotId = medication.optLong("id", 0L).takeIf { it > 0L }
                val restoredId = db.insertOrThrow("user_medication", null, ContentValues().apply {
                    snapshotId?.let { put("id", it) }
                    put("name", medication.optString("name"))
                    put("strength", medication.optString("strength"))
                    put("active_substance", medication.optString("activeSubstance"))
                    put("package_size", medication.optString("packageSize"))
                    put("unit", medication.optString("unit"))
                    put("current_stock", medication.optInt("currentStock", 0))
                    put("dosage", medication.optString("dosage"))
                    put("alert_days", medication.optInt("alertDays", 0))
                    put("last_stock_update_utc", lastStockUpdateUtc)
                })
                snapshotId?.let { medicationIdMap[it] = restoredId }
            }

            val reminders = payload.optJSONArray("reminders") ?: JSONArray()
            for (index in 0 until reminders.length()) {
                val reminder = reminders.optJSONObject(index) ?: continue
                val restoredReminderId = db.insertOrThrow("medication_reminder", null, ContentValues().apply {
                    put("hour", reminder.optInt("hour", 8).coerceIn(0, 23))
                    put("minute", reminder.optInt("minute", 0).coerceIn(0, 59))
                    put("day_mask", reminder.optInt("dayMask", 0))
                    put("enabled", if (reminder.optBoolean("enabled", true)) 1 else 0)
                    put("label", reminder.optString("label"))
                    put("sound_name", reminder.optString("soundName", DEFAULT_REMINDER_SOUND))
                })
                val medicationIds = reminder.optJSONArray("medicationIds") ?: JSONArray()
                for (medicationIndex in 0 until medicationIds.length()) {
                    val restoredMedicationId = medicationIdMap[medicationIds.optLong(medicationIndex)] ?: continue
                    db.insertOrThrow("medication_reminder_medication", null, ContentValues().apply {
                        put("reminder_id", restoredReminderId)
                        put("medication_id", restoredMedicationId)
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        restorePreferences(payload.optJSONObject("preferences"))
        medicationRepository.reconcileStockDepletion()
        return BackupRestoreResult(
            medicationCount = payload.optJSONArray("medications")?.length() ?: 0,
            reminderCount = payload.optJSONArray("reminders")?.length() ?: 0,
        )
    }

    private fun findLocalRestorableSnapshot(accountEmail: String): BackupSnapshotMetadata? {
        return readRestorableSnapshotMetadata(snapshotFile(), accountEmail)
    }

    private fun readRestorableSnapshotMetadata(snapshot: File, accountEmail: String): BackupSnapshotMetadata? {
        if (!snapshot.isFile) return null
        val payload = runCatching { JSONObject(snapshot.readText()) }.getOrNull() ?: return null
        val snapshotAccountEmail = payload.optString("accountEmail")
        if (snapshotAccountEmail.isNotBlank() && !snapshotAccountEmail.equals(accountEmail, ignoreCase = true)) {
            return null
        }
        return BackupSnapshotMetadata(
            createdAtUtc = payload.optLong("createdAtUtc", snapshot.lastModified()),
            medicationCount = payload.optJSONArray("medications")?.length() ?: 0,
            reminderCount = payload.optJSONArray("reminders")?.length() ?: 0,
        )
    }

    private fun restorePreferences(preferences: JSONObject?) {
        preferences ?: return
        val themeMode = AppThemeMode.fromPreferenceValue(preferences.optString("themeMode"))
        val colorPalette = AppColorPalette.fromPreferenceValue(preferences.optString("colorPalette"))
        val languageMode = AppLanguageMode.fromPreferenceValue(preferences.optString("languageMode"))
        ThemeManager.setThemeMode(appContext, themeMode)
        ThemeManager.setColorPalette(appContext, colorPalette)
        LocaleManager.setLanguageMode(appContext, languageMode)
    }

    private fun backupCreatedAtAsDatabaseDateTime(payload: JSONObject): String {
        val createdAtUtc = payload.optLong("createdAtUtc", System.currentTimeMillis())
        return DATABASE_DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(createdAtUtc).atZone(ZoneOffset.UTC))
    }

    private fun snapshotFile(): File = File(File(appContext.filesDir, BACKUP_DIRECTORY), BACKUP_FILE_NAME)

    companion object {
        const val SNAPSHOT_FORMAT = "medstock-user-data-v2"
        const val BACKUP_DIRECTORY = "drive_backup"
        const val BACKUP_FILE_NAME = "medstock_medications_backup.json"
        private const val DEFAULT_REMINDER_SOUND = "dzwonki"
        private val DATABASE_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}


data class BackupSnapshotMetadata(
    val createdAtUtc: Long,
    val medicationCount: Int,
    val reminderCount: Int,
)

data class BackupRestoreResult(
    val medicationCount: Int,
    val reminderCount: Int,
)
