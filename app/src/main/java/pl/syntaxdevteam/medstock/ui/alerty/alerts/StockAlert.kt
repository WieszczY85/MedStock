package pl.syntaxdevteam.medstock.ui.alerty.alerts

import pl.syntaxdevteam.medstock.core.stock.MedicationStockStatus

data class StockAlert(
    val medicationId: Long,
    val medicationName: String,
    val currentStock: Int,
    val unit: String,
    val daysSupply: Int,
    val status: MedicationStockStatus,
)
