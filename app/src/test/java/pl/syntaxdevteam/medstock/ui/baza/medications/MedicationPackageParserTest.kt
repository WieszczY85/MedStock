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
        assertEquals(MedicationPackageInfo("05909990991914", "Rp", "30 tabl."), result[0])
        assertEquals(MedicationPackageInfo("05909990419173", "Rp", "90 tabl."), result[1])
        assertEquals(MedicationPackageInfo("05909991013806", "Rp", "60 tabl."), result[2])
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

    @Test
    fun `parsuje ilosc opakowania z tej samej linii co EAN`() {
        val input = """
            05909990991914 ¦ Rp ¦ 30 tabl.
            05909990419173 ¦ Rp ¦ 90 tabl.
        """.trimIndent()

        val result = MedicationPackageParser.parse("", input, "Brak danych")

        assertEquals(2, result.size)
        assertEquals(MedicationPackageInfo("05909990991914", "Rp", "30 tabl."), result[0])
        assertEquals(MedicationPackageInfo("05909990419173", "Rp", "90 tabl."), result[1])
    }

    @Test
    fun `nie pokazuje brak danych gdy opakowanie ma opis bez EAN`() {
        val input = "Blister 20 tabletek powlekanych"

        val result = MedicationPackageParser.parse("", input, "Brak danych")

        assertEquals(1, result.size)
        assertEquals("-", result[0].ean)
        assertTrue(result[0].quantity.contains("20"))
        assertTrue(result[0].quantity.contains("tablet", ignoreCase = true))
    }

    @Test
    fun `parsuje sekwencje EAN z iloscia w nastepnej linii dla danych z rejestru`() {
        val input = """
            05909991023782 ¦ Rp ¦ 12
            10 tabl.
            05909991023799 ¦ Rp ¦ 13
            20 tabl.
            05909991023805 ¦ Rp ¦ 14
            30 tabl.
            05909991023829 ¦ Rp ¦ 15
            50 tabl.
            05909991023836 ¦ Rp ¦ 16
            60 tabl.
            05909991066185 ¦ Rp ¦ 84657
            40 tabl.
        """.trimIndent()

        val result = MedicationPackageParser.parse("", input, "Brak danych")

        assertEquals(6, result.size)
        assertEquals(MedicationPackageInfo("05909991023782", "Rp", "10 tabl."), result[0])
        assertEquals(MedicationPackageInfo("05909991023799", "Rp", "20 tabl."), result[1])
        assertEquals(MedicationPackageInfo("05909991023805", "Rp", "30 tabl."), result[2])
        assertEquals(MedicationPackageInfo("05909991023829", "Rp", "50 tabl."), result[3])
        assertEquals(MedicationPackageInfo("05909991023836", "Rp", "60 tabl."), result[4])
        assertEquals(MedicationPackageInfo("05909991066185", "Rp", "40 tabl."), result[5])
    }

    @Test
    fun `parsuje kategorie dostepnosci z opakowania`() {
        val input = "05909990991914 ¦ OTC ¦ 20505\n30 tabl."

        val result = MedicationPackageParser.parse("", input, "Brak danych")

        assertEquals(1, result.size)
        assertEquals("OTC", result.first().availabilityCode)
        assertEquals("30 tabl.", result.first().quantity)
    }

}
