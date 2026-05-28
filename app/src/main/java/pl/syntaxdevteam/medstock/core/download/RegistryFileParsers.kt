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
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.ZipFile
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
        val delimiter = delimiterFor(source)
        val headers = try {
            BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8), CSV_READ_BUFFER_SIZE).use { reader ->
                reader.readLine()?.let { splitCsvLine(it, delimiter) }.orEmpty()
            }
        } catch (exception: Exception) {
            throw RegistryFileParsingException(source, "Nie udało się odczytać nagłówków CSV", exception)
        }

        return ParsedRegistryFile(
            source = source,
            headers = headers,
            records = streamCsvRecords(source, file, delimiter)
        )
    }

    private fun streamCsvRecords(source: RegistryFileSource, file: File, delimiter: Char): Sequence<RawRegistryRecord> = sequence {
        try {
            BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8), CSV_READ_BUFFER_SIZE).use { reader ->
                reader.readLine() ?: return@use
                var rowNumber = 1L
                while (true) {
                    val line = reader.readLine() ?: break
                    yield(RawRegistryRecord(source = source, rowNumber = rowNumber, values = splitCsvLine(line, delimiter)))
                    rowNumber += 1L
                }
            }
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
        val result = ArrayList<String>(line.count { it == delimiter } + 1)
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result += normalizeCsvValue(current)
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }

        result += normalizeCsvValue(current)
        return result
    }

    private fun normalizeCsvValue(value: StringBuilder): String = value.toString().trim().removePrefix("\uFEFF")

    private companion object {
        const val CSV_READ_BUFFER_SIZE = 64 * 1024
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

        ZipFile(file).use { zip ->
            val sharedStrings = readXlsxSharedStrings(zip)
            val sheetEntry = findFirstWorksheetEntry(zip)
                ?: return ParsedRegistryFile(source, emptyList(), emptySequence())

            BufferedWriter(OutputStreamWriter(spoolFile.outputStream(), Charsets.UTF_8), TSV_READ_BUFFER_SIZE).use { writer ->
                zip.getInputStream(sheetEntry).use { input ->
                    parseXlsxSheet(input.reader(Charsets.UTF_8), sharedStrings, headers, writer)
                }
            }
        }

        val records = spooledTsvRecords(source, spoolFile)

        ParsedRegistryFile(source = source, headers = headers, records = records)
    } catch (exception: Exception) {
        throw RegistryFileParsingException(source, "Nie udało się sparsować arkusza XLSX strumieniowo", exception)
    }
}

private fun readXlsxSharedStrings(zip: ZipFile): List<String> {
    val entry = zip.getEntry(XLSX_SHARED_STRINGS_ENTRY) ?: return emptyList()
    val handler = SharedStringsHandler()
    zip.getInputStream(entry).use { input ->
        newSaxParser().parse(InputSource(input), handler)
    }
    return handler.strings
}

private class SharedStringsHandler : DefaultHandler() {
    val strings = ArrayList<String>()
    private val current = StringBuilder()
    private var insideSharedString = false
    private var insideText = false

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (xmlName(localName, qName)) {
            "si" -> {
                current.setLength(0)
                insideSharedString = true
            }
            "t" -> if (insideSharedString) insideText = true
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (insideText) current.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (xmlName(localName, qName)) {
            "t" -> insideText = false
            "si" -> {
                strings += current.toString()
                insideSharedString = false
            }
        }
    }
}

private fun findFirstWorksheetEntry(zip: ZipFile) =
    zip.getEntry(XLSX_FIRST_WORKSHEET_ENTRY) ?: run {
        val entries = zip.entries()
        var fallback: java.util.zip.ZipEntry? = null
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory && entry.name.startsWith(XLSX_WORKSHEET_PREFIX) && entry.name.endsWith(".xml")) {
                if (fallback == null || entry.name < fallback.name) fallback = entry
            }
        }
        fallback
    }

private fun parseXlsxSheet(
    reader: java.io.Reader,
    sharedStrings: List<String>,
    headers: MutableList<String>,
    writer: BufferedWriter
) {
    val handler = XlsxSheetHandler(sharedStrings, headers, writer)
    newSaxParser().parse(InputSource(reader), handler)
}

private class XlsxSheetHandler(
    private val sharedStrings: List<String>,
    private val headers: MutableList<String>,
    private val writer: BufferedWriter
) : DefaultHandler() {
    private val currentRow = ArrayList<String>(EXPECTED_RPL_COLUMN_COUNT)
    private val cellValue = StringBuilder(AVERAGE_CELL_CHARS)
    private val lineBuilder = StringBuilder(EXPECTED_RPL_COLUMN_COUNT * AVERAGE_CELL_CHARS)
    private var rowNumber = 0
    private var currentCellIndex = 0
    private var maxCellIndex = -1
    private var cellType: String? = null
    private var insideCell = false
    private var insideValue = false
    private var insideInlineText = false

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (xmlName(localName, qName)) {
            "row" -> {
                currentRow.clear()
                maxCellIndex = -1
                rowNumber = attributes?.getValue("r")?.toIntOrNull() ?: (rowNumber + 1)
            }
            "c" -> {
                insideCell = true
                cellValue.setLength(0)
                cellType = attributes?.getValue("t")
                currentCellIndex = columnIndexFromCellReference(attributes?.getValue("r"))
            }
            "v" -> if (insideCell) insideValue = true
            "t" -> if (insideCell && cellType == "inlineStr") insideInlineText = true
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (insideValue || insideInlineText) cellValue.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (xmlName(localName, qName)) {
            "v" -> insideValue = false
            "t" -> insideInlineText = false
            "c" -> {
                ensureSize(currentRow, currentCellIndex + 1)
                currentRow[currentCellIndex] = resolveXlsxCellValue(cellType, cellValue.toString(), sharedStrings)
                if (currentCellIndex > maxCellIndex) maxCellIndex = currentCellIndex
                insideCell = false
                cellType = null
            }
            "row" -> writeXlsxRow(rowNumber, maxCellIndex, currentRow, headers, lineBuilder, writer)
        }
    }
}

private fun writeXlsxRow(
    rowNumber: Int,
    maxCellIndex: Int,
    currentRow: List<String>,
    headers: MutableList<String>,
    lineBuilder: StringBuilder,
    writer: BufferedWriter
) {
    if (maxCellIndex < 0) return
    if (rowNumber == 1) {
        headers.clear()
        for (index in 0..maxCellIndex) headers += currentRow.getOrNull(index).orEmpty()
        return
    }

    lineBuilder.setLength(0)
    lineBuilder.append(rowNumber - 1)
    for (index in 0..maxCellIndex) {
        lineBuilder.append('\t')
        appendTsvSafe(lineBuilder, currentRow.getOrNull(index).orEmpty())
    }
    writer.append(lineBuilder)
    writer.newLine()
}

private fun resolveXlsxCellValue(cellType: String?, rawValue: String, sharedStrings: List<String>): String =
    when (cellType) {
        "s" -> rawValue.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
        "b" -> when (rawValue) {
            "1" -> "TRUE"
            "0" -> "FALSE"
            else -> rawValue
        }
        else -> rawValue
    }

private fun columnIndexFromCellReference(ref: String?): Int {
    if (ref.isNullOrBlank()) return 0
    var result = 0
    for (ch in ref) {
        if (!ch.isLetter()) break
        result = result * 26 + (ch.uppercaseChar() - 'A' + 1)
    }
    return (result - 1).coerceAtLeast(0)
}

private fun newSaxParser() = SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser()

private fun xmlName(localName: String?, qName: String?): String =
    (localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty()).substringAfter(':')

private fun spooledTsvRecords(source: RegistryFileSource, spoolFile: File): Sequence<RawRegistryRecord> = sequence {
    try {
        BufferedReader(InputStreamReader(spoolFile.inputStream(), Charsets.UTF_8), TSV_READ_BUFFER_SIZE).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val firstSep = line.indexOf('\t')
                if (firstSep <= 0) continue
                val rowNo = line.substring(0, firstSep).toLongOrNull() ?: continue
                yield(RawRegistryRecord(source = source, rowNumber = rowNo, values = splitTsvValues(line, firstSep + 1)))
            }
        }
    } finally {
        spoolFile.delete()
    }
}

private fun splitTsvValues(line: String, startIndex: Int): List<String> {
    val values = ArrayList<String>(EXPECTED_RPL_COLUMN_COUNT)
    var valueStart = startIndex
    var index = startIndex
    while (index < line.length) {
        if (line[index] == '\t') {
            values += line.substring(valueStart, index)
            valueStart = index + 1
        }
        index += 1
    }
    values += line.substring(valueStart)
    return values
}

private fun appendTsvSafe(builder: StringBuilder, value: String) {
    for (char in value) {
        builder.append(if (char == '\t') ' ' else char)
    }
}

private fun ensureSize(values: MutableList<String>, size: Int) {
    while (values.size < size) values += ""
}

private const val TSV_READ_BUFFER_SIZE = 64 * 1024
private const val XLSX_SHARED_STRINGS_ENTRY = "xl/sharedStrings.xml"
private const val XLSX_FIRST_WORKSHEET_ENTRY = "xl/worksheets/sheet1.xml"
private const val XLSX_WORKSHEET_PREFIX = "xl/worksheets/sheet"
private const val EXPECTED_RPL_COLUMN_COUNT = 32
private const val EXPECTED_RA_COLUMN_COUNT = 28
private const val AVERAGE_CELL_CHARS = 18

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

        val records = spooledTsvRecords(source, spoolFile)

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
    private val formatter = HSSFDataFormatter()
    private val rowValues = ArrayList<String>(EXPECTED_RA_COLUMN_COUNT)
    private val lineBuilder = StringBuilder(EXPECTED_RA_COLUMN_COUNT * AVERAGE_CELL_CHARS)
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
                putValue(number.row.toInt(), number.column.toInt(), formatter.formatRawCellContents(number.value, -1, null))
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
                formatter.formatRawCellContents(record.value, -1, null)
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
        ensureSize(rowValues, col + 1)
        rowValues[col] = value.replace('\t', ' ')
        if (col > maxCellIndex) maxCellIndex = col
    }

    private fun flushRow(rowNum: Int) {
        if (rowValues.isEmpty() && maxCellIndex < 0) return

        if (rowNum == 0) {
            headers.clear()
            for (idx in 0..maxCellIndex) headers += rowValues.getOrNull(idx).orEmpty()
        } else {
            lineBuilder.setLength(0)
            lineBuilder.append(rowNum)
            for (idx in 0..maxCellIndex) {
                lineBuilder.append('\t')
                appendTsvSafe(lineBuilder, rowValues.getOrNull(idx).orEmpty())
            }
            writer.append(lineBuilder)
            writer.newLine()
        }

        rowValues.clear()
        maxCellIndex = -1
    }
}
