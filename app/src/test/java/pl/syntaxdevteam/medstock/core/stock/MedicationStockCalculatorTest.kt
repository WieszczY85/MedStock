package pl.syntaxdevteam.medstock.core.stock

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication

class MedicationStockCalculatorTest {

    @Test
    fun parseDailyUsageSumsMorningNoonAndEveningValues() {
        assertEquals(4.0, MedicationStockCalculator.parseDailyUsage("1 tabl. rano + 2 w południe + 1 tabl. wieczorem"), 0.0)
    }

    @Test
    fun calculateRoundsDaysSupplyDown() {
        val stockInfo = MedicationStockCalculator.calculate(
            medication(currentStock = 18, dosage = "1 rano + 2 południe + 1 wieczorem", alertDays = 3)
        )

        assertEquals(4, stockInfo.daysSupply)
        assertEquals(MedicationStockStatus.OK, stockInfo.status)
    }

    @Test
    fun calculateMarksLowWhenDaysSupplyIsAtOrBelowThreshold() {
        val stockInfo = MedicationStockCalculator.calculate(
            medication(currentStock = 18, dosage = "1 rano + 2 południe + 1 wieczorem", alertDays = 4)
        )

        assertEquals(MedicationStockStatus.LOW, stockInfo.status)
    }

    @Test
    fun calculateMarksEmptyOnlyWhenStockIsEmpty() {
        val stockInfo = MedicationStockCalculator.calculate(
            medication(currentStock = 0, dosage = "1 rano + 2 południe + 1 wieczorem", alertDays = 4)
        )

        assertEquals(0, stockInfo.daysSupply)
        assertEquals(MedicationStockStatus.EMPTY, stockInfo.status)
    }

    private fun medication(currentStock: Int, dosage: String, alertDays: Int): UserMedication {
        return UserMedication(
            id = 1L,
            name = "Lek",
            strength = "",
            activeSubstance = "",
            packageSize = "",
            unit = "tabl.",
            currentStock = currentStock,
            dosage = dosage,
            alertDays = alertDays,
        )
    }
}
