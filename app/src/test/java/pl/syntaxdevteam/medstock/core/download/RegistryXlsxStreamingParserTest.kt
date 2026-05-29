package pl.syntaxdevteam.medstock.core.download

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RegistryXlsxStreamingParserTest {

    private val parser = XlsxRegistryFileParser()

    @Test
    fun `streaming parser reads headers and rows from xlsx`() {
        val file = createSampleXlsx()

        val parsed = parser.parse(RegistryFileSource.RPL_XLSX, file)
        val records = parsed.records.toList()

        assertEquals(
            listOf(
                "Identyfikator Produktu Leczniczego",
                "Nazwa produktu leczniczego",
                "Nazwa powszechnie stosowana"
            ),
            parsed.headers
        )
        assertEquals(2, records.size)
        assertEquals(listOf("1001", "Lek A", "Substancja A"), records[0].values)
        assertEquals(listOf("1002", "Lek B", "Substancja B"), records[1].values)
    }

    @Test
    fun `streaming parser reads inline strings and sparse cells from rpl-style xlsx`() {
        val file = createInlineStringXlsx()

        val parsed = parser.parse(RegistryFileSource.RPL_XLSX, file)
        val records = parsed.records.toList()

        assertEquals(
            listOf(
                "Identyfikator Produktu Leczniczego",
                "Nazwa Produktu Leczniczego",
                "Nazwa powszechnie stosowana",
                "Opakowanie"
            ),
            parsed.headers
        )
        assertEquals(2, records.size)
        assertEquals(listOf("2001", "Lek C", "", "30 tabl."), records[0].values)
        assertEquals(listOf("2002", "Lek D", "Substancja D", ""), records[1].values)
    }


    @Test
    fun `streaming parser pads sparse RPL rows to header width for document columns`() {
        val file = createDocumentInlineStringXlsx()

        val parsed = parser.parse(RegistryFileSource.RPL_XLSX, file)
        val records = parsed.records.toList()

        assertEquals(2, records.size)
        assertEquals(5, records[0].values.size)
        assertEquals("Austria", records[0].values[2])
        assertEquals("https://example.test/leaflet", records[0].values[3])
        assertEquals("https://example.test/characteristic", records[0].values[4])
        assertEquals(listOf("3002", "", "", "", ""), records[1].values)
    }

    private fun createSampleXlsx(): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("RPL")
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("Identyfikator Produktu Leczniczego")
        header.createCell(1).setCellValue("Nazwa produktu leczniczego")
        header.createCell(2).setCellValue("Nazwa powszechnie stosowana")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("1001")
        row1.createCell(1).setCellValue("Lek A")
        row1.createCell(2).setCellValue("Substancja A")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("1002")
        row2.createCell(1).setCellValue("Lek B")
        row2.createCell(2).setCellValue("Substancja B")

        val file = File.createTempFile("rpl_streaming_", ".xlsx")
        file.outputStream().use { workbook.write(it) }
        workbook.close()
        return file
    }

    private fun createInlineStringXlsx(): File {
        val file = File.createTempFile("rpl_inline_streaming_", ".xlsx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writestr(
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """.trimIndent()
            )
            zip.writestr(
                "xl/worksheets/sheet1.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="inlineStr"><is><t>Identyfikator Produktu Leczniczego</t></is></c>
                            <c r="B1" t="inlineStr"><is><t>Nazwa Produktu Leczniczego</t></is></c>
                            <c r="C1" t="inlineStr"><is><t>Nazwa powszechnie stosowana</t></is></c>
                            <c r="D1" t="inlineStr"><is><t>Opakowanie</t></is></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="inlineStr"><is><t>2001</t></is></c>
                            <c r="B2" t="inlineStr"><is><t>Lek C</t></is></c>
                            <c r="D2" t="inlineStr"><is><t>30 tabl.</t></is></c>
                        </row>
                        <row r="3">
                            <c r="A3" t="inlineStr"><is><t>2002</t></is></c>
                            <c r="B3" t="inlineStr"><is><t>Lek D</t></is></c>
                            <c r="C3" t="inlineStr"><is><t>Substancja D</t></is></c>
                        </row>
                    </sheetData>
                </worksheet>
                """.trimIndent()
            )
        }
        return file
    }


    private fun createDocumentInlineStringXlsx(): File {
        val file = File.createTempFile("rpl_documents_inline_", ".xlsx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writestr(
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """.trimIndent()
            )
            zip.writestr(
                "xl/worksheets/sheet1.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="inlineStr"><is><t>Identyfikator Produktu Leczniczego</t></is></c>
                            <c r="B1" t="inlineStr"><is><t>Opakowanie</t></is></c>
                            <c r="C1" t="inlineStr"><is><t>Kraj wytwórcy</t></is></c>
                            <c r="D1" t="inlineStr"><is><t>Ulotka</t></is></c>
                            <c r="E1" t="inlineStr"><is><t>Charakterystyka</t></is></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="inlineStr"><is><t>3001</t></is></c>
                            <c r="B2" t="inlineStr"><is><t>1 fiol.
                            5 ml</t></is></c>
                            <c r="C2" t="inlineStr"><is><t>Austria</t></is></c>
                            <c r="D2" t="inlineStr"><is><t>https://example.test/leaflet</t></is></c>
                            <c r="E2" t="inlineStr"><is><t>https://example.test/characteristic</t></is></c>
                        </row>
                        <row r="3">
                            <c r="A3" t="inlineStr"><is><t>3002</t></is></c>
                        </row>
                    </sheetData>
                </worksheet>
                """.trimIndent()
            )
        }
        return file
    }

    private fun ZipOutputStream.writestr(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
