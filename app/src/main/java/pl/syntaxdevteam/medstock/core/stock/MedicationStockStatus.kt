package pl.syntaxdevteam.medstock.core.stock

import kotlin.math.floor
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication

enum class MedicationStockStatus {
    OK,
    LOW,
    EMPTY,
}

data class MedicationStockInfo(
    val dailyUsage: Double,
    val daysSupply: Int,
    val status: MedicationStockStatus,
)

object MedicationStockCalculator {

    fun calculate(medication: UserMedication): MedicationStockInfo {
        val dailyUsage = parseDailyUsage(medication.dosage)
        val daysSupply = when {
            medication.currentStock <= 0 -> 0
            dailyUsage > 0.0 -> floor(medication.currentStock / dailyUsage).toInt()
            else -> Int.MAX_VALUE
        }
        val status = when {
            medication.currentStock <= 0 -> MedicationStockStatus.EMPTY
            dailyUsage > 0.0 && medication.alertDays > 0 && daysSupply <= medication.alertDays -> MedicationStockStatus.LOW
            else -> MedicationStockStatus.OK
        }
        return MedicationStockInfo(
            dailyUsage = dailyUsage,
            daysSupply = daysSupply,
            status = status,
        )
    }

    fun parseDailyUsage(rawDosage: String): Double {
        val normalized = rawDosage.replace(',', '.')
        return Regex("""\d+(?:\.\d+)?""")
            .findAll(normalized)
            .mapNotNull { it.value.toDoubleOrNull() }
            .filter { it > 0.0 }
            .sum()
    }
}
