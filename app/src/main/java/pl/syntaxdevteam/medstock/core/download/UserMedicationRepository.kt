package pl.syntaxdevteam.medstock.core.download

import android.content.ContentValues
import android.content.Context
import pl.syntaxdevteam.medstock.ui.transform.UserMedication

class UserMedicationRepository(context: Context) {

    private val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)

    fun getAll(): List<UserMedication> {
        val db = dbHelper.readableDatabase
        val query = """
            SELECT id, name, strength, active_substance, package_size, unit, current_stock, dosage, alert_days
            FROM user_medication
            ORDER BY updated_at_utc DESC, id DESC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            val medications = mutableListOf<UserMedication>()
            while (cursor.moveToNext()) {
                medications += UserMedication(
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
            }
            return medications
        }
    }

    fun insert(
        name: String,
        strength: String,
        activeSubstance: String,
        packageSize: String,
        unit: String,
        currentStock: Int,
        dosage: String,
        alertDays: Int
    ): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", name)
            put("strength", strength)
            put("active_substance", activeSubstance)
            put("package_size", packageSize)
            put("unit", unit)
            put("current_stock", currentStock)
            put("dosage", dosage)
            put("alert_days", alertDays)
        }
        return db.insertOrThrow("user_medication", null, values)
    }

    fun update(
        id: Long,
        name: String,
        strength: String,
        activeSubstance: String,
        packageSize: String,
        unit: String,
        currentStock: Int,
        dosage: String,
        alertDays: Int
    ): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", name)
            put("strength", strength)
            put("active_substance", activeSubstance)
            put("package_size", packageSize)
            put("unit", unit)
            put("current_stock", currentStock)
            put("dosage", dosage)
            put("alert_days", alertDays)
            put("updated_at_utc", "datetime('now')")
        }
        return db.update("user_medication", values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long): Int {
        return dbHelper.writableDatabase.delete("user_medication", "id = ?", arrayOf(id.toString()))
    }
}
