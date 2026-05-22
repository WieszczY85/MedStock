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
