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

    private fun createTempCsv(content: String): File {
        val file = File.createTempFile("registry_", ".csv")
        file.writeText(content)
        return file
    }
}
