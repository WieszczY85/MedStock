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
        cleanupLegacyRegistryTables(db)
        if (oldVersion < 2) {
            migrateRplWithoutKodEan(db)
            RegistryIngestSchema.statements.forEach(db::execSQL)
        }
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
        if (oldVersion < 9) {
            RegistryIngestSchema.statements.forEach(db::execSQL)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        RegistryIngestSchema.statements.forEach(db::execSQL)
        cleanupLegacyRegistryTables(db)
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) return true
            }
        }
        return false
    }

    private fun migrateRplWithoutKodEan(db: SQLiteDatabase) {
        if (!tableExists(db, "rpl") || !columnExists(db, table = "rpl", column = "kod_ean")) return

        db.execSQL("DROP TABLE IF EXISTS rpl_without_kod_ean")
        db.execSQL(
            """
            CREATE TABLE rpl_without_kod_ean (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                data_snapshot TEXT NOT NULL,
                identyfikator_produktu TEXT,
                nazwa_produktu TEXT,
                droga_podania TEXT,
                moc TEXT,
                postac_farmaceutyczna TEXT,
                podmiot_odpowiedzialny TEXT,
                opakowanie TEXT,
                substancja_czynna TEXT,
                kraj_wytworcy TEXT,
                ulotka TEXT,
                charakterystyka TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO rpl_without_kod_ean(
                id,data_snapshot,identyfikator_produktu,nazwa_produktu,droga_podania,moc,postac_farmaceutyczna,
                podmiot_odpowiedzialny,opakowanie,substancja_czynna,kraj_wytworcy,ulotka,charakterystyka
            )
            SELECT id,data_snapshot,identyfikator_produktu,nazwa_produktu,droga_podania,moc,postac_farmaceutyczna,
                   podmiot_odpowiedzialny,opakowanie,substancja_czynna,kraj_wytworcy,ulotka,charakterystyka
            FROM rpl
            """.trimIndent()
        )
        db.execSQL("DROP TABLE rpl")
        db.execSQL("ALTER TABLE rpl_without_kod_ean RENAME TO rpl")
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(table)
        ).use { cursor ->
            return cursor.moveToFirst()
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

    private fun cleanupLegacyRegistryTables(db: SQLiteDatabase) {
        val legacyTables = listOf(
            "registry_import_batch",
            "registry_rpl_column_dictionary", "registry_rpl_row", "registry_rpl_cell", "registry_rpl_snapshot",
            "registry_ra_column_dictionary", "registry_ra_row", "registry_ra_cell", "registry_ra_snapshot",
            "registry_rdg_column_dictionary", "registry_rdg_row", "registry_rdg_cell"
        )
        legacyTables.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
    }
}
