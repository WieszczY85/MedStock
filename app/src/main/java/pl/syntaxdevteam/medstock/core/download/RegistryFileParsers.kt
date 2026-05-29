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
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
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
                readCsvRecord(reader, delimiter)?.values.orEmpty()
            }
        } catch (exception: Exception) {
            throw RegistryFileParsingException(source, "Nie udało się odczytać nagłówków CSV", exception)
        }

        return ParsedRegistryFile(
            source = source,
            headers = headers,
            records = streamCsvRecords(source, file, delimiter, headers.size)
        )
    }

    private fun streamCsvRecords(source: RegistryFileSource, file: File, delimiter: Char, expectedColumnCount: Int): Sequence<RawRegistryRecord> = sequence {
        try {
            BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8), CSV_READ_BUFFER_SIZE).use { reader ->
                readCsvRecord(reader, delimiter) ?: return@use
                var rowNumber = 1L
                while (true) {
                    val record = readCsvRecord(reader, delimiter) ?: break
                    yield(
                        RawRegistryRecord(
                            source = source,
                            rowNumber = rowNumber,
                            values = alignRecordValues(record.values, expectedColumnCount)
                        )
                    )
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

    private data class CsvRecord(val values: List<String>)

    private fun readCsvRecord(reader: Reader, delimiter: Char): CsvRecord? {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var readAny = false

        while (true) {
            val next = reader.read()
            if (next == -1) {
                if (!readAny && current.isEmpty() && result.isEmpty()) return null
                result += normalizeCsvValue(current)
                return CsvRecord(result)
            }

            readAny = true
            val char = next.toChar()
            when {
                char == '"' && inQuotes -> {
                    reader.mark(1)
                    val escaped = reader.read()
                    if (escaped == '"'.code) {
                        current.append('"')
                    } else {
                        inQuotes = false
                        if (escaped != -1) reader.reset()
                    }
                }
                char == '"' -> inQuotes = true
                char == delimiter && !inQuotes -> {
                    result += normalizeCsvValue(current)
                    current.clear()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r') {
                        reader.mark(1)
                        val maybeLf = reader.read()
                        if (maybeLf != '\n'.code && maybeLf != -1) reader.reset()
                    }
                    result += normalizeCsvValue(current)
                    return CsvRecord(result)
                }
                else -> current.append(char)
            }
        }
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
            val handler = RegistryXmlSaxHandler(source)
            FileInputStream(file).use { input ->
                newSaxParser().parse(InputSource(input), handler)
            }
            ParsedRegistryFile(source = source, headers = listOf("tag", "value"), records = handler.records.asSequence())
        } catch (exception: Exception) {
            throw RegistryFileParsingException(source, "Nie udało się sparsować XML", exception)
        }
    }
}

private class RegistryXmlSaxHandler(
    private val source: RegistryFileSource
) : DefaultHandler() {
    val records = mutableListOf<RawRegistryRecord>()
    private val tagStack = ArrayList<String>()
    private val text = StringBuilder()
    private var rowCounter = 0L

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        tagStack += xmlName(localName, qName)
        text.setLength(0)
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        text.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val value = text.toString().trim()
        if (value.isNotEmpty()) {
            rowCounter += 1
            records += RawRegistryRecord(
                source = source,
                rowNumber = rowCounter,
                values = listOf(tagStack.lastOrNull().orEmpty(), value)
            )
        }
        if (tagStack.isNotEmpty()) tagStack.removeAt(tagStack.lastIndex)
        text.setLength(0)
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

        val records = spooledTsvRecords(source, spoolFile, headers.size)

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

private fun spooledTsvRecords(source: RegistryFileSource, spoolFile: File, expectedColumnCount: Int): Sequence<RawRegistryRecord> = sequence {
    try {
        BufferedReader(InputStreamReader(spoolFile.inputStream(), Charsets.UTF_8), TSV_READ_BUFFER_SIZE).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val firstSep = line.indexOf('\t')
                if (firstSep <= 0) continue
                val rowNo = line.substring(0, firstSep).toLongOrNull() ?: continue
                yield(RawRegistryRecord(source = source, rowNumber = rowNo, values = alignRecordValues(splitTsvValues(line, firstSep + 1), expectedColumnCount)))
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
            values += decodeTsvValue(line.substring(valueStart, index))
            valueStart = index + 1
        }
        index += 1
    }
    values += decodeTsvValue(line.substring(valueStart))
    return values
}

private fun appendTsvSafe(builder: StringBuilder, value: String) {
    for (char in value) {
        when (char) {
            '\\' -> builder.append("\\\\")
            '\t' -> builder.append("\\t")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            else -> builder.append(char)
        }
    }
}

private fun decodeTsvValue(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '\\' && index + 1 < value.length) {
            when (value[index + 1]) {
                '\\' -> append('\\')
                't' -> append('\t')
                'n' -> append('\n')
                'r' -> append('\r')
                else -> append(value[index + 1])
            }
            index += 2
        } else {
            append(char)
            index += 1
        }
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

private fun alignRecordValues(values: List<String>, expectedColumnCount: Int): List<String> = when {
    expectedColumnCount <= 0 -> values
    values.size == expectedColumnCount -> values
    values.size > expectedColumnCount -> values.take(expectedColumnCount)
    else -> values + List(expectedColumnCount - values.size) { "" }
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
                POIFSFileSystem(input).use { poifs ->
                    HSSFEventFactory().processWorkbookEvents(request, poifs)
                }
            }
            handler.flushPendingFormulaString()
        }

        val records = spooledTsvRecords(source, spoolFile, headers.size)

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
