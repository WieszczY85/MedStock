package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RegistryPersistenceIntegrationTest {

    @Test
    fun `same day reingest replaces rows in rpl`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(RegistryIngestDatabaseHelper.DATABASE_NAME)

        val db = RegistryIngestDatabaseHelper(context).writableDatabase
        val orchestrator = RegistryIngestionOrchestrator(
            downloader = TemporaryRegistryFileDownloader(context),
            parsers = RegistryFileParsers(),
            persistence = RegistrySnapshotPersistence(db),
            clock = Clock.fixed(Instant.parse("2026-05-21T10:00:00Z"), ZoneOffset.UTC)
        )

        val fileV1 = tempCsv("Identyfikator Produktu Leczniczego;Nazwa Produktu Leczniczego\n1;Lek A\n2;Lek B\n")
        val fileV2 = tempCsv("Identyfikator Produktu Leczniczego;Nazwa Produktu Leczniczego\n1;Lek A2\n")

        orchestrator.ingestFile(RegistryFileSource.RPL_CSV, fileV1)
        orchestrator.ingestFile(RegistryFileSource.RPL_CSV, fileV2)

        db.rawQuery("SELECT COUNT(DISTINCT data_snapshot) FROM rpl", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.rawQuery("SELECT COUNT(*) FROM rpl WHERE data_snapshot = '2026-05-21'", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.rawQuery("SELECT COUNT(*) FROM rpl", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    private fun tempCsv(content: String): File {
        val file = File.createTempFile("ingest_", ".csv")
        file.writeText(content)
        return file
    }
}
