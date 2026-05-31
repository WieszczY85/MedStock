package pl.syntaxdevteam.medstock.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageCodeNormalizerTest {

    @Test
    fun lookupVariantsAddsLeadingZeroCandidateForShortPackageCode() {
        assertEquals(
            listOf("590123412345", "0590123412345"),
            PackageCodeNormalizer.lookupVariants("590123412345")
        )
    }

    @Test
    fun lookupVariantsAddsGtIn14CandidateForEan13() {
        assertEquals(
            listOf("5909990991914", "05909990991914"),
            PackageCodeNormalizer.lookupVariants("5909990991914")
        )
    }

    @Test
    fun lookupVariantsKeepsTrimmedLeadingZeroCandidate() {
        assertEquals(
            listOf("0590123412345", "590123412345"),
            PackageCodeNormalizer.lookupVariants("0 590 123 412 345")
        )
    }

    @Test
    fun normalizeExtractsGtinFromGs1DataMatrixWithParenthesizedAi() {
        assertEquals(
            "05909990991914",
            PackageCodeNormalizer.normalize("(01)05909990991914(21)ABC123(17)260531")
        )
    }

    @Test
    fun normalizeExtractsGtinFromCompactGs1DataMatrixPayload() {
        assertEquals(
            "05909990991914",
            PackageCodeNormalizer.normalize("010590999099191421ABC123")
        )
    }

    @Test
    fun lookupVariantsForGs1DataMatrixIncludesEan13Candidate() {
        assertEquals(
            listOf("05909990991914", "5909990991914"),
            PackageCodeNormalizer.lookupVariants("(01)05909990991914(21)ABC123")
        )
    }

    @Test
    fun normalizeExtractsGtinFromCompactGs1PayloadContainingOnlyAi01() {
        assertEquals(
            "05909990991914",
            PackageCodeNormalizer.normalize("0105909990991914")
        )
    }
}
