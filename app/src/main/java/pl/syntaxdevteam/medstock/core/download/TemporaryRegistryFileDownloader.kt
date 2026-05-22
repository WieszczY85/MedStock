package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/**
 * Pobiera pliki rejestrowe do dedykowanego katalogu tymczasowego w cache.
 *
 * Klasa automatycznie sprząta stare pliki, aby nie zaśmiecać pamięci podręcznej urządzenia.
 * Zwracany [TemporaryDownloadedFile] należy wyczyścić po przetworzeniu przez parser.
 */
class TemporaryRegistryFileDownloader(
    context: Context,
    private val connectionTimeoutMs: Int = DEFAULT_CONNECTION_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val maxFileAge: Duration = DEFAULT_MAX_FILE_AGE,
    private val maxStoredFiles: Int = DEFAULT_MAX_STORED_FILES
) {
    private val tag = "RegistryDownloader"

    private val tempDirectory: File = File(context.cacheDir, TEMP_DIRECTORY_NAME).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    @Throws(RegistryFileDownloadException::class)
    fun download(source: RegistryFileSource): TemporaryDownloadedFile {
        cleanup()

        val targetFile = File.createTempFile(source.filePrefix, source.fileSuffix, tempDirectory)

        try {
            Log.d(tag, "Downloading ${source.name} from ${source.url} to ${targetFile.absolutePath}")
            downloadToFile(source.url, targetFile)
            Log.d(tag, "Downloaded ${source.name}: ${targetFile.length()} bytes")
        } catch (exception: Exception) {
            targetFile.delete()
            throw RegistryFileDownloadException(
                source = source,
                message = "Nie udało się pobrać pliku źródłowego: ${source.url}",
                cause = exception
            )
        }

        return TemporaryDownloadedFile(source = source, file = targetFile)
    }

    fun cleanup() {
        val allFiles = tempDirectory.listFiles()?.toList().orEmpty()
        val now = System.currentTimeMillis()

        val outdatedFiles = allFiles.filter { file ->
            now - file.lastModified() > maxFileAge.toMillis()
        }
        outdatedFiles.forEach { it.delete() }

        val remainingFiles = tempDirectory.listFiles()
            ?.filter { it.isFile }
            .orEmpty()
            .sortedByDescending { it.lastModified() }

        if (remainingFiles.size > maxStoredFiles) {
            remainingFiles
                .drop(maxStoredFiles)
                .forEach { it.delete() }
        }
    }

    private fun downloadToFile(url: String, targetFile: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectionTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in HTTP_OK_MIN..HTTP_OK_MAX) {
                throw IllegalStateException("Błąd HTTP: $responseCode")
            }

            connection.inputStream.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TEMP_DIRECTORY_NAME = "registry_temp"
        private const val HTTP_OK_MIN = 200
        private const val HTTP_OK_MAX = 299

        const val DEFAULT_CONNECTION_TIMEOUT_MS = 15_000
        const val DEFAULT_READ_TIMEOUT_MS = 30_000
        val DEFAULT_MAX_FILE_AGE: Duration = Duration.ofHours(2)
        const val DEFAULT_MAX_STORED_FILES = 3
    }
}
