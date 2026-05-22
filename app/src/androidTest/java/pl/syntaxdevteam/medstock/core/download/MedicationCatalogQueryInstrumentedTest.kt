package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationCatalogQueryInstrumentedTest {

    @Test
    fun queryForMedicationCatalogReturnsLatestSnapshotRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(RegistryIngestDatabaseHelper.DATABASE_NAME)
        val db = RegistryIngestDatabaseHelper(context).writableDatabase
        val persistence = RegistrySnapshotPersistence(db)

        persistence.saveSnapshot(
            source = RegistryFileSource.RPL_CSV,
            sourceUrl = "https://example/rpl.csv",
            fileName = "rpl.csv",
            fileSha256 = "sha-v2",
            snapshotDateUtc = "2026-05-22",
            fetchedAtUtc = "2026-05-22T10:00:00Z",
            parsed = ParsedRegistryFile(
                source = RegistryFileSource.RPL_CSV,
                headers = listOf("Identyfikator Produktu Leczniczego", "Nazwa produktu leczniczego", "Nazwa powszechnie stosowana"),
                records = sequenceOf(
                    RawRegistryRecord(RegistryFileSource.RPL_CSV, 1, listOf("2001", "Lek A", "Substancja A")),
                    RawRegistryRecord(RegistryFileSource.RPL_CSV, 2, listOf("2002", "Lek B", "Substancja B"))
                )
            )
        )

        db.rawQuery(
            """
            SELECT b.snapshot_date_utc,
                   b.record_count,
                   r.source_entity_key,
                   COALESCE(MAX(CASE WHEN c.column_key = 'Nazwa produktu leczniczego' THEN cell.value_text END), ''),
                   COALESCE(MAX(CASE WHEN c.column_key = 'Nazwa powszechnie stosowana' THEN cell.value_text END), '')
            FROM registry_import_batch b
            JOIN registry_row r ON r.batch_id = b.id
            JOIN registry_cell cell ON cell.row_id = r.id
            JOIN registry_column_dictionary c ON c.id = cell.column_id
            WHERE b.source_code IN ('RPL_CSV', 'RPL_XLSX')
              AND b.snapshot_date_utc = (
                  SELECT MAX(snapshot_date_utc)
                  FROM registry_import_batch
                  WHERE source_code IN ('RPL_CSV', 'RPL_XLSX')
              )
            GROUP BY b.snapshot_date_utc, b.record_count, r.id, r.source_entity_key
            ORDER BY r.source_row_number ASC
            LIMIT 200
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-05-22", cursor.getString(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals("2001", cursor.getString(2))
        }
    }
}
