package pl.syntaxdevteam.medstock.ui.medicationlist

import android.content.Context
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper
import pl.syntaxdevteam.medstock.ui.baza.medications.MedicationPackageParser

data class MedicationCatalogSuggestion(
    val displayName: String,
    val medicationName: String,
    val strength: String,
    val packageSize: String,
    val packageUnit: String,
    val packageDescription: String,
    val pharmaceuticalForm: String,
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
                COALESCE(postac_farmaceutyczna, ''),
                COALESCE(substancja_czynna, '')
            FROM rpl
            WHERE UPPER(COALESCE(nazwa_produktu, '')) LIKE ?
            ORDER BY nazwa_produktu COLLATE NOCASE ASC, moc COLLATE NOCASE ASC, opakowanie COLLATE NOCASE ASC
            LIMIT ?
        """.trimIndent()

        val nameLike = "%${normalized.uppercase()}%"
        db.rawQuery(sql, arrayOf(nameLike, limit.toString())).use { cursor ->
            val items = mutableListOf<MedicationCatalogSuggestion>()
            while (cursor.moveToNext()) {
                val medicationName = cursor.getString(0).orEmpty().trim()
                val strength = cursor.getString(1).orEmpty().trim()
                val packageDescription = cursor.getString(2).orEmpty().trim()
                val pharmaceuticalForm = cursor.getString(3).orEmpty().trim()
                val activeSubstance = cursor.getString(4).orEmpty().trim()
                if (medicationName.isBlank()) continue

                items += createSuggestion(
                    medicationName = medicationName,
                    strength = strength,
                    packageDescription = packageDescription,
                    pharmaceuticalForm = pharmaceuticalForm,
                    activeSubstance = activeSubstance,
                    matchedPackageCode = null,
                )
            }
            return items
        }
    }

    fun findByPackageCode(rawCode: String): MedicationCatalogSuggestion? {
        val normalizedCode = rawCode.filter(Char::isDigit)
        if (normalizedCode.length < MIN_PACKAGE_CODE_LENGTH) return null

        val db = dbHelper.readableDatabase
        val sql = """
            SELECT
                COALESCE(nazwa_produktu, ''),
                COALESCE(moc, ''),
                COALESCE(opakowanie, ''),
                COALESCE(postac_farmaceutyczna, ''),
                COALESCE(substancja_czynna, '')
            FROM rpl
            WHERE COALESCE(opakowanie, '') LIKE ?
            ORDER BY nazwa_produktu COLLATE NOCASE ASC, moc COLLATE NOCASE ASC
            LIMIT 1
        """.trimIndent()

        db.rawQuery(sql, arrayOf("%$normalizedCode%")).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val medicationName = cursor.getString(0).orEmpty().trim()
            if (medicationName.isBlank()) return null
            return createSuggestion(
                medicationName = medicationName,
                strength = cursor.getString(1).orEmpty().trim(),
                packageDescription = cursor.getString(2).orEmpty().trim(),
                pharmaceuticalForm = cursor.getString(3).orEmpty().trim(),
                activeSubstance = cursor.getString(4).orEmpty().trim(),
                matchedPackageCode = normalizedCode,
            )
        }
    }

    private fun createSuggestion(
        medicationName: String,
        strength: String,
        packageDescription: String,
        pharmaceuticalForm: String,
        activeSubstance: String,
        matchedPackageCode: String?,
    ): MedicationCatalogSuggestion {
        val packageInfo = extractPackageInfo(packageDescription, matchedPackageCode)
        return MedicationCatalogSuggestion(
            displayName = buildDisplayName(medicationName, strength, packageInfo.displayPackage),
            medicationName = medicationName,
            strength = strength,
            packageSize = packageInfo.size,
            packageUnit = pharmaceuticalForm.ifBlank { packageInfo.unit },
            packageDescription = packageDescription,
            pharmaceuticalForm = pharmaceuticalForm,
            activeSubstance = activeSubstance,
        )
    }

    companion object {
        private const val MIN_PACKAGE_CODE_LENGTH = 6

        internal fun buildDisplayName(name: String, strength: String, displayPackage: String): String {
            val details = listOf(strength, displayPackage)
                .filter { it.isNotBlank() }
                .joinToString(separator = " ")
            return if (details.isBlank()) name else "$name ($details)"
        }

        internal fun extractPackageInfo(packageDescription: String, matchedPackageCode: String? = null): PackageInfo {
            val selectedQuantity = selectPackageQuantity(packageDescription, matchedPackageCode)
            val normalized = selectedQuantity
                .replace(Regex("\\s+"), " ")
                .trim(' ', ';', ',')
            val match = Regex("""(?i)(\d+(?:[,.]\d+)?)\s*([\p{L}.]+\.?(?:\s+[\p{L}.]+\.?)?)?""").find(normalized)
            val size = match?.groupValues?.getOrNull(1).orEmpty().replace(',', '.').trim()
            val unit = match?.groupValues?.getOrNull(2).orEmpty().trim(' ', ';', ',')
            val displayPackage = when {
                size.isNotBlank() -> listOf(size, unit)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = " ")
                else -> normalized
            }
            return PackageInfo(size = size, unit = unit, displayPackage = displayPackage)
        }

        private fun selectPackageQuantity(packageDescription: String, matchedPackageCode: String?): String {
            val packages = MedicationPackageParser.parse(
                kodEan = "",
                opakowanie = packageDescription,
                unknownPackageLabel = "",
            )
            if (packages.isEmpty()) return packageDescription

            val normalizedMatchedCode = matchedPackageCode.orEmpty().filter(Char::isDigit)
            val selected = if (normalizedMatchedCode.isNotBlank()) {
                packages.firstOrNull { item -> item.ean.filter(Char::isDigit) == normalizedMatchedCode }
                    ?: packages.firstOrNull { item -> item.ean.filter(Char::isDigit).contains(normalizedMatchedCode) }
            } else {
                packages.firstOrNull { item -> item.quantity.isNotBlank() }
            }

            return selected?.quantity.orEmpty().ifBlank { packages.first().quantity }
        }
    }
}

data class PackageInfo(
    val size: String,
    val unit: String,
    val displayPackage: String,
)
