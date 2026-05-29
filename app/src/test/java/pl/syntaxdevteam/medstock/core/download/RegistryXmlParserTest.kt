package pl.syntaxdevteam.medstock.core.download

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RegistryXmlParserTest {

    private val parser = XmlRegistryFileParser()

    @Test
    fun `RDG XML parser keeps non-empty leaf text records`() {
        val file = File.createTempFile("rdg_", ".xml")
        file.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Decyzje>
                <Decyzja>
                    <NumerDecyzji>ABC-123</NumerDecyzji>
                    <Opis>Linia 1
                    Linia 2</Opis>
                </Decyzja>
            </Decyzje>
            """.trimIndent()
        )

        val parsed = parser.parse(RegistryFileSource.RDG_XML, file)
        val records = parsed.records.toList()

        assertEquals(listOf("tag", "value"), parsed.headers)
        assertEquals(listOf("NumerDecyzji", "ABC-123"), records[0].values)
        assertEquals("Opis", records[1].values[0])
        assertEquals("Linia 1\n        Linia 2", records[1].values[1])
    }
}
