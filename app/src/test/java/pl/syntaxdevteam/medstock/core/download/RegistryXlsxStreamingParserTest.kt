package pl.syntaxdevteam.medstock.core.download

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

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
}
