package pl.syntaxdevteam.medstock.core.barcode

object PackageCodeNormalizer {

    /**
     * Returns the package identifier that can be matched against RPL packaging data.
     *
     * Medicine packs are often scanned as GS1 DataMatrix, where the raw value contains
     * multiple application identifiers (GTIN, serial number, expiry date, lot number).
     * For lookup purposes we must keep only AI (01), i.e. the 14-digit GTIN.
     */
    fun normalize(rawCode: String): String {
        val gs1Gtin = extractGs1Gtin(rawCode)
        if (gs1Gtin.isNotBlank()) return gs1Gtin
        return rawCode.filter(Char::isDigit)
    }

    fun normalizeScannerValues(rawValue: String?, displayValue: String?, rawBytes: ByteArray? = null): String {
        val rawBytesValue = rawBytes?.toString(Charsets.UTF_8)
        return sequenceOf(rawValue, displayValue, rawBytesValue)
            .map { it.orEmpty().trim() }
            .map(::normalize)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

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

    private fun extractGs1Gtin(rawCode: String): String {
        GS1_TEXT_AI_01_REGEX.find(rawCode)?.let { match ->
            return match.groupValues[1]
        }

        val digits = rawCode.filter(Char::isDigit)
        return when {
            digits.length >= GS1_AI_01_PREFIX.length + GTIN_14_LENGTH && digits.startsWith(GS1_AI_01_PREFIX) -> {
                digits.substring(GS1_AI_01_PREFIX.length, GS1_AI_01_PREFIX.length + GTIN_14_LENGTH)
            }
            else -> ""
        }
    }

    private const val GS1_AI_01_PREFIX = "01"
    private const val GTIN_14_LENGTH = 14
    private val CANDIDATE_LENGTHS_FOR_LEADING_ZERO = 12..13
    private val GS1_TEXT_AI_01_REGEX = Regex("""(?:^|[^\d])\(?01\)?(\d{14})(?=\D|$)""")
}
