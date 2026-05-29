package pl.syntaxdevteam.medstock.core.download

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RegistryXlsStreamingParserTest {

    private val parser = XlsRegistryFileParser()

    @Test
    fun `streaming parser keeps RA XLS fields after multiline cells`() {
        val workbook = HSSFWorkbook()
        val sheet = workbook.createSheet("RA")
        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("identyfikator_apteki")
        header.createCell(1).setCellValue("nazwa_apteki")
        header.createCell(2).setCellValue("wojewodztwo")
        header.createCell(3).setCellValue("miejscowosc")

        val row = sheet.createRow(1)
        row.createCell(0).setCellValue("10001")
        row.createCell(1).setCellValue("Apteka\nTest")
        row.createCell(2).setCellValue("Mazowieckie")
        row.createCell(3).setCellValue("Warszawa")

        val file = File.createTempFile("ra_multiline_", ".xls")
        file.outputStream().use { workbook.write(it) }
        workbook.close()

        val parsed = parser.parse(RegistryFileSource.RA_XLS, file)
        val record = parsed.records.first()

        assertEquals(listOf("identyfikator_apteki", "nazwa_apteki", "wojewodztwo", "miejscowosc"), parsed.headers)
        assertEquals(listOf("10001", "Apteka\nTest", "Mazowieckie", "Warszawa"), record.values)
    }
}
