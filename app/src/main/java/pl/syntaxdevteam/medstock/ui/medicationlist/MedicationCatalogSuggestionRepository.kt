package pl.syntaxdevteam.medstock.ui.medicationlist

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
            ORDER BY nazwa_produktu COLLATE NOCASE ASC, moc COLLATE NOCASE ASC, CASE WHEN opakowanie GLOB '*[A-Za-z]*' THEN 0 ELSE 1 END, opakowanie COLLATE NOCASE ASC
            LIMIT ?
        """.trimIndent()

        val nameLike = "%${normalized.uppercase()}%"
        db.rawQuery(sql, arrayOf(nameLike, (limit * SEARCH_OVERFETCH_FACTOR).toString())).use { cursor ->
            val items = mutableListOf<MedicationCatalogSuggestion>()
            while (cursor.moveToNext()) {
                val medicationName = cursor.getString(0).orEmpty().trim()
                val strength = cursor.getString(1).orEmpty().trim()
                val packageDescription = cursor.getString(2).orEmpty().trim()
                val pharmaceuticalForm = cursor.getString(3).orEmpty().trim()
                val activeSubstance = cursor.getString(4).orEmpty().trim()
                if (medicationName.isBlank()) continue

                val suggestion = createSuggestion(
                    medicationName = medicationName,
                    strength = strength,
                    packageDescription = packageDescription,
                    pharmaceuticalForm = pharmaceuticalForm,
                    activeSubstance = activeSubstance,
                    matchedPackageCode = null,
                )
                if (suggestion.packageSize.isBlank() && containsPackageCode(packageDescription)) continue
                if (items.none { item -> item.displayName == suggestion.displayName }) {
                    items += suggestion
                }
                if (items.size >= limit) break
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
            val strength = cursor.getString(1).orEmpty().trim()
            val packageDescription = cursor.getString(2).orEmpty().trim()
            val pharmaceuticalForm = cursor.getString(3).orEmpty().trim()
            val activeSubstance = cursor.getString(4).orEmpty().trim()
            return createSuggestion(
                medicationName = medicationName,
                strength = strength,
                packageDescription = readCombinedPackageDescription(
                    db = db,
                    medicationName = medicationName,
                    strength = strength,
                    pharmaceuticalForm = pharmaceuticalForm,
                    activeSubstance = activeSubstance,
                    fallbackPackageDescription = packageDescription,
                ),
                pharmaceuticalForm = pharmaceuticalForm,
                activeSubstance = activeSubstance,
                matchedPackageCode = normalizedCode,
            )
        }
    }

    private fun readCombinedPackageDescription(
        db: SQLiteDatabase,
        medicationName: String,
        strength: String,
        pharmaceuticalForm: String,
        activeSubstance: String,
        fallbackPackageDescription: String,
    ): String {
        val sql = """
            SELECT COALESCE(opakowanie, '')
            FROM rpl
            WHERE COALESCE(nazwa_produktu, '') = ?
              AND COALESCE(moc, '') = ?
              AND COALESCE(postac_farmaceutyczna, '') = ?
              AND COALESCE(substancja_czynna, '') = ?
            ORDER BY id ASC
            LIMIT 200
        """.trimIndent()
        db.rawQuery(sql, arrayOf(medicationName, strength, pharmaceuticalForm, activeSubstance)).use { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.moveToNext()) {
                cursor.getString(0).orEmpty().trim().takeIf { it.isNotBlank() }?.let(rows::add)
            }
            return rows.joinToString(separator = "\n").ifBlank { fallbackPackageDescription }
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
        private const val SEARCH_OVERFETCH_FACTOR = 4

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
            if (packages.isEmpty()) {
                return packageDescription.takeUnless { containsPackageCode(it) }.orEmpty()
            }

            val normalizedMatchedCode = matchedPackageCode.orEmpty().filter(Char::isDigit)
            val selected = if (normalizedMatchedCode.isNotBlank()) {
                packages.firstOrNull { item -> item.ean.filter(Char::isDigit) == normalizedMatchedCode }
                    ?: packages.firstOrNull { item -> item.ean.filter(Char::isDigit).contains(normalizedMatchedCode) }
            } else {
                packages.firstOrNull { item -> item.quantity.isNotBlank() }
            }

            return selected?.quantity.orEmpty()
                .ifBlank { packages.firstOrNull { item -> item.quantity.isNotBlank() }?.quantity.orEmpty() }
                .takeUnless { containsPackageCode(it) }
                .orEmpty()
        }

        internal fun containsPackageCode(value: String): Boolean {
            return Regex("""\b\d{8,14}\b""").containsMatchIn(value)
        }
    }
}

data class PackageInfo(
    val size: String,
    val unit: String,
    val displayPackage: String,
)
