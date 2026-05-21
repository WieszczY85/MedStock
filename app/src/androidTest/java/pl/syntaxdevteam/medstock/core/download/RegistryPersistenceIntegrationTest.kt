package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RegistryPersistenceIntegrationTest {

    @Test
    fun `same day reingest upserts batch and replaces rows`() {
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

        db.rawQuery("SELECT COUNT(*) FROM registry_import_batch", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.rawQuery("SELECT record_count FROM registry_import_batch", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.rawQuery("SELECT COUNT(*) FROM registry_row", emptyArray()).use {
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
