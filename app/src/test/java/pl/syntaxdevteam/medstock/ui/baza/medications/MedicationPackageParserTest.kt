package pl.syntaxdevteam.medstock.ui.baza.medications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationPackageParserTest {

    @Test
    fun `parsuje wiele opakowan EAN i tabletki`() {
        val input = """
            05909990991914 ¦ Rp ¦ 20505
            30 tabl.
            05909990419173 ¦ Rp ¦ 29516
            90 tabl.
            05909991013806 ¦ Rp ¦ 79842
            60 tabl.
        """.trimIndent()

        val result = MedicationPackageParser.parse("", input, "Brak danych")

        assertEquals(3, result.size)
        assertEquals(MedicationPackageInfo("05909990991914", "30 tabl."), result[0])
        assertEquals(MedicationPackageInfo("05909990419173", "90 tabl."), result[1])
        assertEquals(MedicationPackageInfo("05909991013806", "60 tabl."), result[2])
    }

    @Test
    fun `nie lapie samych liczb bez jednostek`() {
        val input = "05909990991914 ¦ Rp ¦ 20505\\n5"

        val result = MedicationPackageParser.parse("05909990991914", input, "Brak danych")

        assertEquals(1, result.size)
        assertEquals("05909990991914", result.first().ean)
        assertEquals("Brak danych", result.first().quantity)
    }

    @Test
    fun `fallback regex dziala dla tekstu jednoliniowego`() {
        val input = "05909990991914 ¦ Rp ¦ 20505 30 tabl. 05909990419173 ¦ Rp ¦ 29516 90 tabl."

        val result = MedicationPackageParser.parse("", input, "Brak danych")

        assertEquals(2, result.size)
        assertTrue(result.all { it.quantity.contains("tabl", ignoreCase = true) })
    }

}
