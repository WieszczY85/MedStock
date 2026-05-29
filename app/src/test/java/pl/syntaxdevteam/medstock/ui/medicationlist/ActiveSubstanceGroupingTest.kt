package pl.syntaxdevteam.medstock.ui.medicationlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSubstanceGroupingTest {

    @Test
    fun normalizeCollapsesWhitespaceAndIgnoresCase() {
        assertEquals(
            "bisoprolol fumarate",
            ActiveSubstanceGrouping.normalize("  Bisoprolol\n\tFUMARATE  ")
        )
    }

    @Test
    fun findMatchesRequiresExactNormalizedActiveSubstance() {
        val medications = listOf(
            medication(id = 1, name = "Bisocard", activeSubstance = "Bisoprolol fumarate"),
            medication(id = 2, name = "Ibuprofen", activeSubstance = "Ibuprofen"),
            medication(id = 3, name = "Bibloc", activeSubstance = "  bisoprolol   fumarate  "),
        )

        val matches = ActiveSubstanceGrouping.findMatches("BISOPROLOL FUMARATE", medications)

        assertEquals(listOf(1L, 3L), matches.map { it.id })
    }

    @Test
    fun findMatchesIgnoresBlankSubstances() {
        val medications = listOf(medication(id = 1, name = "Blank", activeSubstance = ""))

        assertTrue(ActiveSubstanceGrouping.findMatches("   ", medications).isEmpty())
    }

    private fun medication(
        id: Long,
        name: String,
        activeSubstance: String,
    ): UserMedication {
        return UserMedication(
            id = id,
            name = name,
            strength = "5 mg",
            activeSubstance = activeSubstance,
            packageSize = "30",
            unit = "tabs.",
            currentStock = 10,
            dosage = "1||",
            alertDays = 7,
        )
    }
}
