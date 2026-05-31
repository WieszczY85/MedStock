package pl.syntaxdevteam.medstock.core.barcode

object PackageCodeNormalizer {

    fun normalize(rawCode: String): String = rawCode.filter(Char::isDigit)

    fun lookupVariants(rawCode: String): List<String> {
        val normalized = normalize(rawCode)
        if (normalized.isBlank()) return emptyList()

        val variants = linkedSetOf(normalized)
        if (normalized.length in CANDIDATE_LENGTHS_FOR_LEADING_ZERO && !normalized.startsWith('0')) {
            variants += "0$normalized"
        }
        val withoutLeadingZeros = normalized.trimStart('0')
        if (withoutLeadingZeros.isNotBlank()) {
            variants += withoutLeadingZeros
        }
        return variants.toList()
    }

    private val CANDIDATE_LENGTHS_FOR_LEADING_ZERO = 12..13
}
