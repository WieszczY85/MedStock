package pl.syntaxdevteam.medstock.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrySnapshotPersistenceHelpersTest {

    @Test
    fun `canonicalizeHeader normalizuje polskie znaki`() {
        assertEquals("droga_podania", canonicalizeHeader("Droga podania"))
        assertEquals("nazwa_powszechnie_stosowana", canonicalizeHeader("Nazwa powszechnie stosowana"))
        assertEquals("godziny_otwarcia_sroda", canonicalizeHeader("Godziny otwarcia środa"))
    }

    @Test
    fun `prepareProjection mapuje aliasy`() {
        val headers = listOf("identyfikator_produktu_leczniczego", "nazwa_produktu_leczniczego", "moc")
        val mapping = linkedMapOf(
            "identyfikator_produktu_leczniczego" to listOf("identyfikator_produktu_leczniczego"),
            "nazwa_produktu_leczniczego" to listOf("nazwa_produktu_leczniczego"),
            "moc" to listOf("moc", "moc_dawki")
        )

        val projection = prepareProjection(headers, mapping)

        assertEquals(0, projection["identyfikator_produktu_leczniczego"])
        assertEquals(1, projection["nazwa_produktu_leczniczego"])
        assertEquals(2, projection["moc"])
    }

    @Test
    fun `serializeRecordPayload tworzy json z escapowaniem`() {
        val payload = serializeRecordPayload(
            headers = listOf("NumerDecyzji", "Opis"),
            values = listOf("ABC-123", "Linia\n\"test\"")
        )

        assertTrue(payload.startsWith("{"))
        assertTrue(payload.endsWith("}"))
        assertTrue(payload.contains("\"NumerDecyzji\":\"ABC-123\""))
        assertTrue(payload.contains("\\n"))
        assertTrue(payload.contains("\\\"test\\\""))
    }
}
