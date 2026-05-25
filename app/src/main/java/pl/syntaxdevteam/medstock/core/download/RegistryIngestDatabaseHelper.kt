package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RegistryIngestDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, RegistryIngestSchema.VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA temp_store = MEMORY")
        db.execSQL("PRAGMA foreign_keys = ON")
    }

    override fun onCreate(db: SQLiteDatabase) {
        RegistryIngestSchema.statements.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        RegistryIngestSchema.statements.forEach(db::execSQL)
        if (oldVersion < 5) {
            ensureColumn(db, table = "user_medication", column = "strength", definition = "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, table = "user_medication", column = "package_description", definition = "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, table = "user_medication", column = "active_substance", definition = "TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 6) {
            ensureColumn(db, table = "user_medication", column = "package_size", definition = "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, table = "user_medication", column = "unit", definition = "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, table = "user_medication", column = "current_stock", definition = "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, table = "user_medication", column = "dosage", definition = "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, table = "user_medication", column = "alert_days", definition = "TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 8) {
            ensureColumn(
                db,
                table = "user_medication",
                column = "last_stock_update_utc",
                definition = "TEXT NOT NULL DEFAULT (datetime('now'))"
            )
        }
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
        var exists = false
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) {
                    exists = true
                    break
                }
            }
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    companion object {
        const val DATABASE_NAME = "registry_ingest.db"

        @Volatile
        private var instance: RegistryIngestDatabaseHelper? = null

        fun getInstance(context: Context): RegistryIngestDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: RegistryIngestDatabaseHelper(context).also { instance = it }
            }
        }
    }
}
