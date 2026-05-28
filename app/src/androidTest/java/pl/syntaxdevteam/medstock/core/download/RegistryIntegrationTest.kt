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
class RegistryIntegrationTest {

    @Test
    fun `ingestFile nadpisuje ten sam snapshot_date w rpl`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(RegistryIngestDatabaseHelper.DATABASE_NAME)

        val helper = RegistryIngestDatabaseHelper(context)
        val db = helper.writableDatabase

        val parser = RegistryFileParsers()
        val persistence = RegistrySnapshotPersistence(db)
        val clock = Clock.fixed(Instant.parse("2026-05-21T09:30:00Z"), ZoneOffset.UTC)
        val orchestrator = RegistryIngestionOrchestrator(
            downloader = TemporaryRegistryFileDownloader(context),
            parsers = parser,
            persistence = persistence,
            clock = clock
        )

        val sampleFile = createSampleRplCsv()
        orchestrator.ingestFile(RegistryFileSource.RPL_CSV, sampleFile)
        orchestrator.ingestFile(RegistryFileSource.RPL_CSV, sampleFile)

        db.rawQuery("SELECT COUNT(DISTINCT data_snapshot) FROM rpl", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.rawQuery("SELECT COUNT(*) FROM rpl WHERE data_snapshot = '2026-05-21'", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
        db.rawQuery("SELECT COUNT(*) FROM rpl", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
    }

    private fun createSampleRplCsv(): File {
        val target = File.createTempFile("sample_", "_rpl.csv")
        target.writeText("""Identyfikator Produktu Leczniczego;Nazwa Produktu Leczniczego;Moc\n1001;Lek A;10mg\n1002;Lek B;20mg\n""".trimIndent())
        return target
    }
}
