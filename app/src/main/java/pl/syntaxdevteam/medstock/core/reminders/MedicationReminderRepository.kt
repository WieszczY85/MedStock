package pl.syntaxdevteam.medstock.core.reminders

import android.content.ContentValues
import android.content.Context
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper
import pl.syntaxdevteam.medstock.ui.alerty.reminders.MedicationReminder
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication

class MedicationReminderRepository(context: Context) {

    private val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)

    fun getAll(): List<MedicationReminder> {
        val reminders = mutableListOf<MedicationReminder>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT id, hour, minute, day_mask, enabled, label
            FROM medication_reminder
            ORDER BY hour ASC, minute ASC, id DESC
        """.trimIndent()
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val meds = getMedicationIds(id)
                reminders += MedicationReminder(
                    id = id,
                    hour = cursor.getInt(1),
                    minute = cursor.getInt(2),
                    dayMask = cursor.getInt(3),
                    enabled = cursor.getInt(4) == 1,
                    label = cursor.getString(5).orEmpty(),
                    medicationIds = meds,
                    medications = getMedications(meds)
                )
            }
        }
        return reminders
    }

    fun findById(id: Long): MedicationReminder? {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT id, hour, minute, day_mask, enabled, label
            FROM medication_reminder
            WHERE id = ?
        """.trimIndent()
        db.rawQuery(query, arrayOf(id.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val meds = getMedicationIds(id)
            return MedicationReminder(
                id = cursor.getLong(0),
                hour = cursor.getInt(1),
                minute = cursor.getInt(2),
                dayMask = cursor.getInt(3),
                enabled = cursor.getInt(4) == 1,
                label = cursor.getString(5).orEmpty(),
                medicationIds = meds,
                medications = getMedications(meds)
            )
        }
    }

    fun insert(hour: Int, minute: Int, dayMask: Int, enabled: Boolean, label: String, medicationIds: List<Long>): Long {
        val db = dbHelper.writableDatabase
        val id = db.insertOrThrow("medication_reminder", null, ContentValues().apply {
            put("hour", hour)
            put("minute", minute)
            put("day_mask", dayMask)
            put("enabled", if (enabled) 1 else 0)
            put("label", label)
        })
        replaceMedications(id, medicationIds)
        return id
    }

    fun update(id: Long, hour: Int, minute: Int, dayMask: Int, enabled: Boolean, label: String, medicationIds: List<Long>): Int {
        val db = dbHelper.writableDatabase
        val updated = db.update("medication_reminder", ContentValues().apply {
            put("hour", hour)
            put("minute", minute)
            put("day_mask", dayMask)
            put("enabled", if (enabled) 1 else 0)
            put("label", label)
            put("updated_at_utc", currentUtcDateTime())
        }, "id = ?", arrayOf(id.toString()))
        replaceMedications(id, medicationIds)
        return updated
    }

    fun setEnabled(id: Long, enabled: Boolean): Int {
        return dbHelper.writableDatabase.update("medication_reminder", ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("updated_at_utc", currentUtcDateTime())
        }, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long): Int {
        return dbHelper.writableDatabase.delete("medication_reminder", "id = ?", arrayOf(id.toString()))
    }

    private fun replaceMedications(reminderId: Long, medicationIds: List<Long>) {
        val db = dbHelper.writableDatabase
        db.delete("medication_reminder_medication", "reminder_id = ?", arrayOf(reminderId.toString()))
        medicationIds.distinct().forEach { medicationId ->
            db.insertOrThrow("medication_reminder_medication", null, ContentValues().apply {
                put("reminder_id", reminderId)
                put("medication_id", medicationId)
            })
        }
    }

    private fun getMedicationIds(reminderId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT medication_id FROM medication_reminder_medication WHERE reminder_id = ? ORDER BY medication_id",
            arrayOf(reminderId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        return ids
    }

    private fun getMedications(ids: List<Long>): List<UserMedication> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val byId = mutableMapOf<Long, UserMedication>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT id, name, strength, active_substance, package_size, unit, current_stock, dosage, alert_days
            FROM user_medication
            WHERE id IN ($placeholders)
            """.trimIndent(),
            ids.map(Long::toString).toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val medication = UserMedication(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    strength = cursor.getString(2).orEmpty(),
                    activeSubstance = cursor.getString(3).orEmpty(),
                    packageSize = cursor.getString(4).orEmpty(),
                    unit = cursor.getString(5).orEmpty(),
                    currentStock = cursor.getInt(6),
                    dosage = cursor.getString(7).orEmpty(),
                    alertDays = cursor.getInt(8)
                )
                byId[medication.id] = medication
            }
        }
        return ids.mapNotNull(byId::get)
    }

    private fun currentUtcDateTime(): String {
        dbHelper.readableDatabase.rawQuery("SELECT datetime('now')", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else ""
        }
    }
}
