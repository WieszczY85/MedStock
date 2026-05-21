package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RegistryIngestDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, RegistryIngestSchema.VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        RegistryIngestSchema.statements.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        RegistryIngestSchema.statements.forEach(db::execSQL)
    }

    companion object {
        const val DATABASE_NAME = "registry_ingest.db"
    }
}
