package pl.syntaxdevteam.medstock.core.download

import android.content.ContentValues
import android.content.Context
import kotlin.math.floor
import pl.syntaxdevteam.medstock.core.stock.MedicationStockCalculator
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication

class UserMedicationRepository(context: Context) {

    private val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)

    fun getAll(): List<UserMedication> {
        reconcileStockDepletion()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT id, name, strength, active_substance, package_size, unit, current_stock, dosage, alert_days, last_stock_update_utc
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
                    alertDays = cursor.getInt(8),
                    lastStockUpdateUtc = cursor.getString(9).orEmpty()
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
        val now = currentUtcDateTime()
        val values = ContentValues().apply {
            put("name", name)
            put("strength", strength)
            put("active_substance", activeSubstance)
            put("package_size", packageSize)
            put("unit", unit)
            put("current_stock", currentStock)
            put("dosage", dosage)
            put("alert_days", alertDays)
            put("last_stock_update_utc", now)
            put("updated_at_utc", now)
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
        val now = currentUtcDateTime()
        val values = ContentValues().apply {
            put("name", name)
            put("strength", strength)
            put("active_substance", activeSubstance)
            put("package_size", packageSize)
            put("unit", unit)
            put("current_stock", currentStock)
            put("dosage", dosage)
            put("alert_days", alertDays)
            put("last_stock_update_utc", now)
            put("updated_at_utc", now)
        }
        return db.update("user_medication", values, "id = ?", arrayOf(id.toString()))
    }

    fun addToStock(id: Long, addedStock: Int): Int {
        val db = dbHelper.writableDatabase
        val now = currentUtcDateTime()
        val statement = """
            UPDATE user_medication
            SET current_stock = current_stock + ?,
                last_stock_update_utc = ?,
                updated_at_utc = ?
            WHERE id = ?
        """.trimIndent()
        return db.compileStatement(statement).use { compiled ->
            compiled.bindLong(1, addedStock.toLong())
            compiled.bindString(2, now)
            compiled.bindString(3, now)
            compiled.bindLong(4, id)
            compiled.executeUpdateDelete()
        }
    }

    fun delete(id: Long): Int {
        return dbHelper.writableDatabase.delete("user_medication", "id = ?", arrayOf(id.toString()))
    }

    fun reconcileStockDepletion(): Int {
        val db = dbHelper.writableDatabase
        val now = currentUtcDateTime()
        val query = """
            SELECT id, current_stock, dosage, last_stock_update_utc
            FROM user_medication
        """.trimIndent()

        var updatedRows = 0
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val currentStock = cursor.getInt(1)
                val dosage = cursor.getString(2).orEmpty()
                val lastStockUpdateUtc = cursor.getString(3).orEmpty()
                val daysElapsed = fullDaysBetween(lastStockUpdateUtc, now)
                if (daysElapsed <= 0) continue
                val dosagePerDay = MedicationStockCalculator.parseDailyUsage(dosage)
                if (dosagePerDay <= 0.0) continue
                val depletion = floor(dosagePerDay * daysElapsed).toInt()
                val updatedStock = (currentStock - depletion).coerceAtLeast(0)
                val values = ContentValues().apply {
                    put("current_stock", updatedStock)
                    put("last_stock_update_utc", now)
                    put("updated_at_utc", now)
                }
                updatedRows += db.update("user_medication", values, "id = ?", arrayOf(id.toString()))
            }
        }
        return updatedRows
    }

    private fun fullDaysBetween(lastStockUpdateUtc: String, currentUtc: String): Long {
        if (lastStockUpdateUtc.isBlank() || currentUtc.isBlank()) return 0
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT CAST((julianday(?) - julianday(?)) AS INTEGER)",
            arrayOf(currentUtc, lastStockUpdateUtc)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0).coerceAtLeast(0)
            }
        }
        return 0
    }

    private fun currentUtcDateTime(): String {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT datetime('now')", null).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return ""
    }
}
