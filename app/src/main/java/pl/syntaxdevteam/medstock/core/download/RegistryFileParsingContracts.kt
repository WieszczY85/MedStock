package pl.syntaxdevteam.medstock.core.download

import java.io.File

data class RawRegistryRecord(
    val source: RegistryFileSource,
    val rowNumber: Long,
    val values: List<String>
)

data class ParsedRegistryFile(
    val source: RegistryFileSource,
    val headers: List<String>,
    val records: Sequence<RawRegistryRecord>
)

interface RegistryFileParser {
    fun supports(source: RegistryFileSource): Boolean
    fun parse(source: RegistryFileSource, file: File): ParsedRegistryFile
}

class RegistryFileParsingException(
    val source: RegistryFileSource,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
