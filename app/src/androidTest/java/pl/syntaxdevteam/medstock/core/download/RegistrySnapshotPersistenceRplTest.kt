package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrySnapshotPersistenceRplTest {

    @Test
    fun `saveSnapshot persists RPL country leaflet and characteristic columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(RegistryIngestDatabaseHelper.DATABASE_NAME)
        val db = RegistryIngestDatabaseHelper(context).writableDatabase
        val persistence = RegistrySnapshotPersistence(db)

        persistence.saveSnapshot(
            source = RegistryFileSource.RPL_CSV,
            sourceUrl = "https://example.test/rpl.csv",
            fileName = "rpl.csv",
            fileSha256 = "sha",
            snapshotDateUtc = "2026-05-29",
            fetchedAtUtc = "2026-05-29T00:00:00Z",
            parsed = ParsedRegistryFile(
                source = RegistryFileSource.RPL_CSV,
                headers = listOf(
                    "Identyfikator Produktu Leczniczego",
                    "Nazwa Produktu Leczniczego",
                    "Opakowanie",
                    "Kraj wytwórcy",
                    "Ulotka",
                    "Charakterystyka"
                ),
                records = sequenceOf(
                    RawRegistryRecord(
                        RegistryFileSource.RPL_CSV,
                        1,
                        listOf(
                            "1001",
                            "Lek A",
                            "1 fiol.\n5 ml",
                            "Polska",
                            "https://example.test/leaflet",
                            "https://example.test/characteristic"
                        )
                    )
                )
            )
        )

        db.rawQuery(
            """
            SELECT kraj_wytworcy, ulotka, charakterystyka
            FROM rpl
            WHERE identyfikator_produktu = '1001'
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Polska", cursor.getString(0))
            assertEquals("https://example.test/leaflet", cursor.getString(1))
            assertEquals("https://example.test/characteristic", cursor.getString(2))
        }
    }
}
