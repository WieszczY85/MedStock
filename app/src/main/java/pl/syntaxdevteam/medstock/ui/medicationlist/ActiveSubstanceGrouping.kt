package pl.syntaxdevteam.medstock.ui.medicationlist

object ActiveSubstanceGrouping {

    fun findMatches(rawActiveSubstance: String, medications: List<UserMedication>): List<UserMedication> {
        val normalizedActiveSubstance = normalize(rawActiveSubstance)
        if (normalizedActiveSubstance.isBlank()) return emptyList()
        return medications.filter { medication ->
            normalize(medication.activeSubstance) == normalizedActiveSubstance
        }
    }

    fun normalize(rawActiveSubstance: String): String {
        return rawActiveSubstance
            .trim()
            .lowercase()
            .replace(Regex("""\s+"""), " ")
    }
}
