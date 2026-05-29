package pl.syntaxdevteam.medstock.ui.medicationlist

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationCatalogSuggestionRepositoryTest {

    @Test
    fun buildDisplayNameUsesNameStrengthAndParsedPackage() {
        val packageInfo = MedicationCatalogSuggestionRepository.extractPackageInfo("30 tabl. | Rp | 20504")

        assertEquals("30", packageInfo.size)
        assertEquals("tabl.", packageInfo.unit)
        assertEquals("30 tabl.", packageInfo.displayPackage)
        assertEquals(
            "Atoris (20 mg 30 tabl.)",
            MedicationCatalogSuggestionRepository.buildDisplayName("Atoris", "20 mg", packageInfo.displayPackage)
        )
    }

    @Test
    fun extractPackageInfoSkipsEanAndUsesQuantityFromNextLine() {
        val packageInfo = MedicationCatalogSuggestionRepository.extractPackageInfo(
            """
            05909990804412 ¦ Rp ¦ 12345
            30 tabl.
            """.trimIndent()
        )

        assertEquals("30", packageInfo.size)
        assertEquals("tabl.", packageInfo.unit)
        assertEquals("30 tabl.", packageInfo.displayPackage)
    }

    @Test
    fun extractPackageInfoUsesQuantityMatchedToScannedEan() {
        val packageInfo = MedicationCatalogSuggestionRepository.extractPackageInfo(
            """
            05909990804412 ¦ Rp ¦ 12345
            30 tabl.
            05909991229566 ¦ Rp ¦ 67890
            60 tabl.
            """.trimIndent(),
            matchedPackageCode = "05909991229566"
        )

        assertEquals("60", packageInfo.size)
        assertEquals("tabl.", packageInfo.unit)
        assertEquals("60 tabl.", packageInfo.displayPackage)
    }


    @Test
    fun extractPackageInfosReturnsEveryPackageVariant() {
        val packageInfos = MedicationCatalogSuggestionRepository.extractPackageInfos(
            """
            05909990991914 ¦ Rp ¦ 20505
            30 tabl.
            05909990419173 ¦ Rp ¦ 29516
            90 tabl.
            05909991013806 ¦ Rp ¦ 79842
            60 tabl.
            """.trimIndent()
        )

        assertEquals(listOf("30 tabl.", "90 tabl.", "60 tabl."), packageInfos.map { it.displayPackage })
    }

    @Test
    fun extractPackageInfoDoesNotExposeEanWhenQuantityIsMissing() {
        val packageInfo = MedicationCatalogSuggestionRepository.extractPackageInfo("05909991129422")

        assertEquals("", packageInfo.size)
        assertEquals("", packageInfo.unit)
        assertEquals("", packageInfo.displayPackage)
        assertEquals(
            "Zofenil 30 (30 mg)",
            MedicationCatalogSuggestionRepository.buildDisplayName("Zofenil 30", "30 mg", packageInfo.displayPackage)
        )
    }
}
