package pl.syntaxdevteam.medstock.ui.medicationlist

data class UserMedication(
    val id: Long,
    val name: String,
    val strength: String,
    val activeSubstance: String,
    val packageSize: String,
    val unit: String,
    val currentStock: Int,
    val dosage: String,
    val alertDays: Int,
)
