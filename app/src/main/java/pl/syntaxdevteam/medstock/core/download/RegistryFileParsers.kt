package pl.syntaxdevteam.medstock.core.download

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class RegistryFileParsers(
    private val parsers: List<RegistryFileParser> = listOf(
        CsvRegistryFileParser(),
        XmlRegistryFileParser(),
        XlsRegistryFileParser(),
        XlsxRegistryFileParser()
    )
) {

    fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile {
        val parser = parsers.firstOrNull { it.supports(source) }
            ?: throw RegistryFileParsingException(source, "Brak parsera dla źródła: $source")
        return parser.parse(source, file)
    }
}

class CsvRegistryFileParser : RegistryFileParser {
    override fun supports(source: RegistryFileSource): Boolean =
        source == RegistryFileSource.RPL_CSV || source == RegistryFileSource.RA_CSV

    override fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile {
        return try {
            val records = mutableListOf<RawRegistryRecord>()
            var headers: List<String> = emptyList()

            BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.lineSequence().forEachIndexed { index, line ->
                    val values = splitCsvLine(line, delimiterFor(source))
                    if (index == 0) {
                        headers = values
                    } else {
                        records += RawRegistryRecord(source = source, rowNumber = index.toLong(), values = values)
                    }
                }
            }

            ParsedRegistryFile(source = source, headers = headers, records = records.asSequence())
        } catch (exception: Exception) {
            throw RegistryFileParsingException(source, "Nie udało się sparsować CSV", exception)
        }
    }

    private fun delimiterFor(source: RegistryFileSource): Char =
        when (source) {
            RegistryFileSource.RPL_CSV -> ';'
            RegistryFileSource.RA_CSV -> '|'
            else -> ','
        }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        line.forEach { char ->
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                }
                char == delimiter && !inQuotes -> {
                    result += current.toString().trim().removePrefix("\uFEFF")
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        result += current.toString().trim().removePrefix("\uFEFF")
        return result
    }
}

class XmlRegistryFileParser : RegistryFileParser {
    override fun supports(source: RegistryFileSource): Boolean = source == RegistryFileSource.RDG_XML

    override fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile {
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            FileInputStream(file).use { input ->
                parser.setInput(InputStreamReader(input, Charsets.UTF_8))
                parseXmlDocument(source, parser)
            }
        } catch (exception: Exception) {
            throw RegistryFileParsingException(source, "Nie udało się sparsować XML", exception)
        }
    }

    private fun parseXmlDocument(source: RegistryFileSource, parser: XmlPullParser): ParsedRegistryFile {
        val records = mutableListOf<RawRegistryRecord>()
        var rowCounter = 0L
        val rootName = mutableListOf<String>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> rootName += parser.name
                XmlPullParser.END_TAG -> if (rootName.isNotEmpty()) rootName.removeAt(rootName.lastIndex)
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        rowCounter += 1
                        records += RawRegistryRecord(
                            source = source,
                            rowNumber = rowCounter,
                            values = listOf(rootName.lastOrNull().orEmpty(), text)
                        )
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedRegistryFile(source = source, headers = listOf("tag", "value"), records = records.asSequence())
    }
}

class XlsRegistryFileParser : RegistryFileParser {
    override fun supports(source: RegistryFileSource): Boolean = source == RegistryFileSource.RA_XLS

    override fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile {
        return parseWorkbook(source, file) { stream -> HSSFWorkbook(stream) }
    }
}

class XlsxRegistryFileParser : RegistryFileParser {
    override fun supports(source: RegistryFileSource): Boolean = source == RegistryFileSource.RPL_XLSX

    override fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile {
        return parseWorkbook(source, file) { stream -> XSSFWorkbook(stream) }
    }
}

private inline fun parseWorkbook(
    source: RegistryFileSource,
    file: File,
    crossinline workbookFactory: (FileInputStream) -> org.apache.poi.ss.usermodel.Workbook
): ParsedRegistryFile {
    return try {
        FileInputStream(file).use { input ->
            workbookFactory(input).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val formatter = DataFormatter()
                val headerRow = sheet.getRow(sheet.firstRowNum)
                val headers = headerRow?.map { formatter.formatCellValue(it) }.orEmpty()
                val records = mutableListOf<RawRegistryRecord>()

                for (index in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                    val row = sheet.getRow(index) ?: continue
                    val values = row.map { cell ->
                        if (cell.cellType == CellType.BLANK) "" else formatter.formatCellValue(cell)
                    }
                    records += RawRegistryRecord(source = source, rowNumber = index.toLong(), values = values)
                }

                ParsedRegistryFile(source = source, headers = headers, records = records.asSequence())
            }
        }
    } catch (exception: Exception) {
        throw RegistryFileParsingException(source, "Nie udało się sparsować arkusza", exception)
    }
}
