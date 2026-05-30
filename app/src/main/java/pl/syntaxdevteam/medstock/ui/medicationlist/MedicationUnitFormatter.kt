package pl.syntaxdevteam.medstock.ui.medicationlist

import java.text.Normalizer
import java.util.Locale

object MedicationUnitFormatter {

    fun abbreviate(rawUnit: String): String {
        val trimmed = rawUnit.trim()
        if (trimmed.isBlank()) return ""

        val normalized = trimmed
            .lowercase(Locale.ROOT)
            .replace('ł', 'l')
            .replace('Ł', 'l')
            .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            .replace(DIACRITICS_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return when {
            normalized.matches(Regex("tabl\\.?")) -> "tabl."
            normalized.matches(Regex("tab\\.?|tabs\\.?|tablets?")) -> "tabs."
            normalized.startsWith("tablet") -> "tabl."
            normalized.matches(Regex("kaps\\.?")) -> "kaps."
            normalized.startsWith("kapsul") || normalized.startsWith("capsule") -> "kaps."
            normalized.matches(Regex("amp\\.?")) -> "amp."
            normalized.startsWith("ampul") || normalized.startsWith("ampoule") -> "amp."
            normalized.matches(Regex("fiol\\.?")) -> "fiol."
            normalized.startsWith("fiolk") || normalized.startsWith("vial") -> "fiol."
            normalized.matches(Regex("sasz\\.?")) -> "sasz."
            normalized.startsWith("saszet") || normalized.startsWith("sachet") -> "sasz."
            normalized.matches(Regex("czop\\.?")) -> "czop."
            normalized.startsWith("czopk") || normalized.startsWith("suppositor") -> "czop."
            normalized.matches(Regex("glob\\.?")) -> "glob."
            normalized.startsWith("globul") -> "glob."
            normalized.matches(Regex("draz\\.?")) -> "draż."
            normalized.startsWith("drazet") -> "draż."
            normalized.matches(Regex("op\\.?")) -> "op."
            normalized.startsWith("opak") || normalized.startsWith("pack") -> "op."
            normalized.matches(Regex("szt\\.?")) -> "szt."
            normalized.startsWith("sztuk") || normalized.startsWith("piece") -> "szt."
            normalized in unchangedLowercaseUnits -> normalized
            else -> trimmed
        }
    }

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val unchangedLowercaseUnits = setOf("ml", "l", "g", "mg", "mcg", "µg", "j", "iu")
}
