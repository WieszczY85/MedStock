package pl.syntaxdevteam.medstock.core.download

import java.io.File

enum class RegistryFileSource(
    val url: String,
    val filePrefix: String,
    val fileSuffix: String
) {
    RDG_XML(
        url = "https://rdg.ezdrowie.gov.pl/Decision/DownloadPublicXml",
        filePrefix = "rdg_",
        fileSuffix = ".xml"
    ),
    RPL_XLSX(
        url = "https://rejestry.ezdrowie.gov.pl/api/rpl/medicinal-products/public-pl-report/get-xlsx",
        filePrefix = "rpl_",
        fileSuffix = ".xlsx"
    ),
    RPL_CSV(
        url = "https://rejestry.ezdrowie.gov.pl/api/rpl/medicinal-products/public-pl-report/get-csv",
        filePrefix = "rpl_",
        fileSuffix = ".csv"
    ),
    RA_XLS(
        url = "https://rejestry.ezdrowie.gov.pl/api/ra/filegenerator/getxls",
        filePrefix = "ra_",
        fileSuffix = ".xls"
    ),
    RA_CSV(
        url = "https://rejestry.ezdrowie.gov.pl/api/ra/filegenerator/getcsv",
        filePrefix = "ra_",
        fileSuffix = ".csv"
    )
}

class RegistryFileDownloadException(
    val source: RegistryFileSource,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

data class TemporaryDownloadedFile(
    val source: RegistryFileSource,
    val file: File
) {
    fun cleanup() {
        file.delete()
    }
}
