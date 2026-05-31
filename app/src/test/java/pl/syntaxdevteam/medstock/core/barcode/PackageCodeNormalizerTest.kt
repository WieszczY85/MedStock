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
}
