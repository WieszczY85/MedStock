package pl.syntaxdevteam.medstock.core.download

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RegistryCsvParserTest {

    private val parser = CsvRegistryFileParser()

    @Test
    fun `RPL CSV uses semicolon delimiter`() {
        val file = createTempCsv(
            "Identyfikator Produktu Leczniczego;Nazwa Produktu Leczniczego;Moc\n1001;Lek A;10mg\n"
        )

        val parsed = parser.parse(RegistryFileSource.RPL_CSV, file)

        assertEquals(listOf("Identyfikator Produktu Leczniczego", "Nazwa Produktu Leczniczego", "Moc"), parsed.headers)
        val row = parsed.records.first()
        assertEquals(listOf("1001", "Lek A", "10mg"), row.values)
    }

    @Test
    fun `RA CSV uses pipe delimiter and trims BOM`() {
        val bom = "\uFEFF"
        val file = createTempCsv(
            "${bom}identyfikator_apteki|nazwa_apteki|wojewodztwo\n10001|Apteka Test|Mazowieckie\n"
        )

        val parsed = parser.parse(RegistryFileSource.RA_CSV, file)

        assertEquals(listOf("identyfikator_apteki", "nazwa_apteki", "wojewodztwo"), parsed.headers)
        val row = parsed.records.first()
        assertEquals(listOf("10001", "Apteka Test", "Mazowieckie"), row.values)
    }

    @Test
    fun `CSV parser streams rows and keeps escaped quotes`() {
        val file = createTempCsv(
            "Identyfikator Produktu Leczniczego;Nazwa Produktu Leczniczego;Moc\n" +
                "1001;\"Lek \"\"A\"\"\";10mg\n" +
                "1002;Lek B;20mg\n"
        )

        val parsed = parser.parse(RegistryFileSource.RPL_CSV, file)
        val iterator = parsed.records.iterator()

        assertEquals(listOf("1001", "Lek \"A\"", "10mg"), iterator.next().values)
        assertEquals(listOf("1002", "Lek B", "20mg"), iterator.next().values)
    }


    @Test
    fun `RPL CSV parser keeps columns after multiline quoted package field`() {
        val file = createTempCsv(
            "Identyfikator Produktu Leczniczego;Opakowanie;Kraj wytwórcy;Ulotka;Charakterystyka\n" +
                "1001;\"0590 ¦ Rp ¦ 1\n1 fiol. 5 ml\";Polska;https://example.test/leaflet;https://example.test/characteristic\n" +
                "1002;\"Lek \"\"B\"\"\";Czechy;https://example.test/leaflet-b;https://example.test/characteristic-b\n"
        )

        val parsed = parser.parse(RegistryFileSource.RPL_CSV, file)
        val records = parsed.records.toList()

        assertEquals(5, parsed.headers.size)
        assertEquals(2, records.size)
        assertEquals(
            listOf(
                "1001",
                "0590 ¦ Rp ¦ 1\n1 fiol. 5 ml",
                "Polska",
                "https://example.test/leaflet",
                "https://example.test/characteristic"
            ),
            records[0].values
        )
        assertEquals("https://example.test/characteristic-b", records[1].values[4])
    }

    private fun createTempCsv(content: String): File {
        val file = File.createTempFile("registry_", ".csv")
        file.writeText(content)
        return file
    }
}
