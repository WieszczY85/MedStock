package pl.syntaxdevteam.medstock.core.download

import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory
import org.apache.poi.hssf.eventusermodel.HSSFRequest
import org.apache.poi.hssf.eventusermodel.MissingRecordAwareHSSFListener
import org.apache.poi.hssf.eventusermodel.dummyrecord.LastCellOfRowDummyRecord
import org.apache.poi.hssf.eventusermodel.dummyrecord.MissingCellDummyRecord
import org.apache.poi.hssf.record.BOFRecord
import org.apache.poi.hssf.record.BoundSheetRecord
import org.apache.poi.hssf.record.LabelSSTRecord
import org.apache.poi.hssf.record.NumberRecord
import org.apache.poi.hssf.record.RKRecord
import org.apache.poi.hssf.record.Record
import org.apache.poi.hssf.record.SSTRecord
import org.apache.poi.hssf.record.StringRecord
import org.apache.poi.hssf.record.FormulaRecord
import org.apache.poi.hssf.usermodel.HSSFDataFormatter
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.apache.poi.xssf.model.StylesTable
import org.apache.poi.xssf.usermodel.XSSFComment
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.xml.parsers.SAXParserFactory

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

    override fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile =
        parseXlsWorkbookStreaming(source, file)
}

class XlsxRegistryFileParser : RegistryFileParser {
    override fun supports(source: RegistryFileSource): Boolean = source == RegistryFileSource.RPL_XLSX

    override fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile {
        return parseWorkbookStreaming(source, file)
    }
}

private fun parseWorkbookStreaming(source: RegistryFileSource, file: File): ParsedRegistryFile {
    return try {
        val spoolFile = File.createTempFile("rpl_stream_", ".tsv")
        val headers = mutableListOf<String>()

        OPCPackage.open(file).use { pkg ->
            val strings = ReadOnlySharedStringsTable(pkg)
            val reader = XSSFReader(pkg)
            val styles: StylesTable = reader.stylesTable
            val formatter = DataFormatter()

            BufferedWriter(OutputStreamWriter(spoolFile.outputStream(), Charsets.UTF_8)).use { writer ->
                val sheetIterator = reader.sheetsData
                if (!sheetIterator.hasNext()) {
                    return ParsedRegistryFile(source, emptyList(), emptySequence())
                }

                sheetIterator.next().use { sheetInput ->
                    val handler = StreamingSheetHandler(headers, writer)
                    val xmlHandler = XSSFSheetXMLHandler(styles, null, strings, handler, formatter, false)
                    val parserFactory = SAXParserFactory.newInstance().apply {
                        isNamespaceAware = true
                    }
                    val parser: XMLReader = parserFactory.newSAXParser().xmlReader
                    parser.contentHandler = xmlHandler
                    parser.parse(InputSource(sheetInput))
                }
            }
        }

        val records = sequence {
            BufferedReader(InputStreamReader(spoolFile.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val firstSep = line.indexOf('\t')
                    if (firstSep <= 0) return@forEach
                    val rowNo = line.substring(0, firstSep).toLongOrNull() ?: return@forEach
                    val values = line.substring(firstSep + 1).split('\t')
                    yield(RawRegistryRecord(source = source, rowNumber = rowNo, values = values))
                }
            }
            spoolFile.delete()
        }

        ParsedRegistryFile(source = source, headers = headers, records = records)
    } catch (exception: Exception) {
        throw RegistryFileParsingException(source, "Nie udało się sparsować arkusza XLSX strumieniowo", exception)
    }
}

private class StreamingSheetHandler(
    private val headers: MutableList<String>,
    private val writer: BufferedWriter
) : XSSFSheetXMLHandler.SheetContentsHandler {
    private val currentRow = mutableMapOf<Int, String>()
    private var maxCellIndex = -1

    override fun startRow(rowNum: Int) {
        currentRow.clear()
        maxCellIndex = -1
    }

    override fun endRow(rowNum: Int) {
        if (rowNum == 0) {
            for (index in 0..maxCellIndex) headers += currentRow[index].orEmpty()
            return
        }
        if (maxCellIndex < 0) return
        val values = (0..maxCellIndex).joinToString("\t") { idx -> currentRow[idx].orEmpty().replace("\t", " ") }
        writer.write("$rowNum\t$values")
        writer.newLine()
    }

    override fun cell(cellReference: String?, formattedValue: String?, comment: XSSFComment?) {
        val index = columnIndex(cellReference)
        currentRow[index] = formattedValue.orEmpty()
        if (index > maxCellIndex) maxCellIndex = index
    }

    override fun headerFooter(text: String?, isHeader: Boolean, tagName: String?) = Unit

    private fun columnIndex(ref: String?): Int {
        if (ref.isNullOrBlank()) return 0
        var result = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            result = result * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return (result - 1).coerceAtLeast(0)
    }
}

private fun parseXlsWorkbookStreaming(source: RegistryFileSource, file: File): ParsedRegistryFile {
    return try {
        val spoolFile = File.createTempFile("ra_stream_", ".tsv")
        val headers = mutableListOf<String>()

        BufferedWriter(OutputStreamWriter(spoolFile.outputStream(), Charsets.UTF_8)).use { writer ->
            val handler = XlsSheetEventHandler(headers, writer)
            val request = HSSFRequest()
            val formatListener = FormatTrackingHSSFListener(handler)
            request.addListenerForAllRecords(MissingRecordAwareHSSFListener(formatListener))
            FileInputStream(file).use { input ->
                HSSFEventFactory().processEvents(request, input)
            }
            handler.flushPendingFormulaString()
        }

        val records = sequence {
            BufferedReader(InputStreamReader(spoolFile.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val firstSep = line.indexOf('\t')
                    if (firstSep <= 0) return@forEach
                    val rowNo = line.substring(0, firstSep).toLongOrNull() ?: return@forEach
                    val values = line.substring(firstSep + 1).split('\t')
                    yield(RawRegistryRecord(source = source, rowNumber = rowNo, values = values))
                }
            }
            spoolFile.delete()
        }

        ParsedRegistryFile(source = source, headers = headers, records = records)
    } catch (exception: Exception) {
        throw RegistryFileParsingException(source, "Nie udało się sparsować arkusza XLS strumieniowo", exception)
    }
}

private class XlsSheetEventHandler(
    private val headers: MutableList<String>,
    private val writer: BufferedWriter
) : org.apache.poi.hssf.eventusermodel.HSSFListener {
    private var sharedStringsTable: SSTRecord? = null
    private var currentRow = -1
    private var currentColumn = -1
    private var currentSheetIndex = -1
    private var targetSheetIndex = 0
    private var inTargetSheet = false
    private var outputNextStringRecord = false
    private var pendingFormulaStringCol = -1
    private val rowValues = mutableMapOf<Int, String>()
    private var maxCellIndex = -1

    override fun processRecord(record: Record) {
        when (record.sid.toInt()) {
            BoundSheetRecord.sid.toInt() -> currentSheetIndex += 1
            BOFRecord.sid.toInt() -> {
                val bof = record as BOFRecord
                if (bof.type == BOFRecord.TYPE_WORKSHEET) {
                    inTargetSheet = currentSheetIndex == targetSheetIndex
                }
            }
            SSTRecord.sid.toInt() -> sharedStringsTable = record as SSTRecord
            FormulaRecord.sid.toInt() -> handleFormulaRecord(record as FormulaRecord)
            StringRecord.sid.toInt() -> handleStringRecord(record as StringRecord)
            LabelSSTRecord.sid.toInt() -> {
                if (!inTargetSheet) return
                val label = record as LabelSSTRecord
                putValue(label.row.toInt(), label.column.toInt(), sharedStringsTable?.getString(label.sstIndex)?.string.orEmpty())
            }
            NumberRecord.sid.toInt() -> {
                if (!inTargetSheet) return
                val number = record as NumberRecord
                putValue(number.row.toInt(), number.column.toInt(), HSSFDataFormatter().formatRawCellContents(number.value, -1, null))
            }
            RKRecord.sid.toInt() -> {
                if (!inTargetSheet) return
                val rk = record as RKRecord
                putValue(rk.row.toInt(), rk.column.toInt(), rk.rkNumber.toString())
            }
        }

        if (!inTargetSheet) return
        when (record) {
            is MissingCellDummyRecord -> {
                currentRow = record.row
                currentColumn = record.column
                if (currentColumn > maxCellIndex) maxCellIndex = currentColumn
            }
            is LastCellOfRowDummyRecord -> {
                flushRow(record.row)
            }
        }
    }

    private fun handleFormulaRecord(record: FormulaRecord) {
        if (!inTargetSheet) return
        val hasString = java.lang.Double.isNaN(record.value)
        if (hasString) {
            outputNextStringRecord = true
            pendingFormulaStringCol = record.column.toInt()
            currentRow = record.row.toInt()
        } else {
            putValue(
                record.row.toInt(),
                record.column.toInt(),
                HSSFDataFormatter().formatRawCellContents(record.value, -1, null)
            )
        }
    }

    private fun handleStringRecord(record: StringRecord) {
        if (!inTargetSheet || !outputNextStringRecord) return
        putValue(currentRow, pendingFormulaStringCol, record.string)
        outputNextStringRecord = false
        pendingFormulaStringCol = -1
    }

    fun flushPendingFormulaString() {
        outputNextStringRecord = false
        pendingFormulaStringCol = -1
    }

    private fun putValue(row: Int, col: Int, value: String) {
        if (row != currentRow) {
            currentRow = row
        }
        currentColumn = col
        rowValues[col] = value.replace("\t", " ")
        if (col > maxCellIndex) maxCellIndex = col
    }

    private fun flushRow(rowNum: Int) {
        if (rowValues.isEmpty() && maxCellIndex < 0) return

        if (rowNum == 0) {
            headers.clear()
            for (idx in 0..maxCellIndex) headers += rowValues[idx].orEmpty()
        } else {
            val values = (0..maxCellIndex).joinToString("\t") { idx -> rowValues[idx].orEmpty() }
            writer.write("$rowNum\t$values")
            writer.newLine()
        }

        rowValues.clear()
        maxCellIndex = -1
    }
}
