package pl.syntaxdevteam.medstock.ui.medicationlist

import android.content.Context
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper

data class MedicationCatalogSuggestion(
    val displayName: String,
    val medicationName: String,
    val strength: String,
    val packageDescription: String,
    val activeSubstance: String,
)

class MedicationCatalogSuggestionRepository(context: Context) {

    private val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)

    fun search(query: String, limit: Int = 15): List<MedicationCatalogSuggestion> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()

        val db = dbHelper.readableDatabase
        val sql = """
            SELECT DISTINCT
                COALESCE(nazwa_produktu, ''),
                COALESCE(moc, ''),
                COALESCE(opakowanie, ''),
                COALESCE(substancja_czynna, '')
            FROM rpl
            WHERE UPPER(COALESCE(nazwa_produktu, '')) LIKE ?
            ORDER BY nazwa_produktu COLLATE NOCASE ASC
            LIMIT ?
        """.trimIndent()

        val nameLike = "%${normalized.uppercase()}%"
        db.rawQuery(sql, arrayOf(nameLike, limit.toString())).use { cursor ->
            val items = mutableListOf<MedicationCatalogSuggestion>()
            while (cursor.moveToNext()) {
                val medicationName = cursor.getString(0).orEmpty().trim()
                val strength = cursor.getString(1).orEmpty().trim()
                val packageDescription = cursor.getString(2).orEmpty().trim()
                val activeSubstance = cursor.getString(3).orEmpty().trim()
                if (medicationName.isBlank()) continue

                val display = buildDisplayName(medicationName, strength, packageDescription)
                items += MedicationCatalogSuggestion(
                    displayName = display,
                    medicationName = medicationName,
                    strength = strength,
                    packageDescription = packageDescription,
                    activeSubstance = activeSubstance,
                )
            }
            return items
        }
    }

    private fun buildDisplayName(name: String, strength: String, packageDescription: String): String {
        val details = listOf(strength, packageDescription)
            .filter { it.isNotBlank() }
            .joinToString(separator = ". ")
        return if (details.isBlank()) name else "$name ($details)"
    }
}
