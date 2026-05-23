package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

data class StartupProgress(
    val progressPercent: Int,
    val message: String
)

class StartupIngestionRunner(private val context: Context) {
    private val tag = "StartupIngestionRunner"

    fun run(): Flow<StartupProgress> = flow {
        val schedule = StartupIngestionSchedule(context)
        if (!schedule.shouldRunNow()) {
            emit(StartupProgress(100, context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_all)))
            return@flow
        }

        val sourcePlans = listOf(
            listOf(RegistryFileSource.RDG_XML),
            listOf(RegistryFileSource.RPL_XLSX, RegistryFileSource.RPL_CSV),
            listOf(RegistryFileSource.RA_CSV, RegistryFileSource.RA_XLS)
        )
        val totalPlans = sourcePlans.size
        var completedPlans = 0

        fun percent(): Int = ((completedPlans * 100f) / totalPlans).toInt().coerceIn(0, 100)

        val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)
        val persistence = RegistrySnapshotPersistence(dbHelper.writableDatabase)
        val downloader = TemporaryRegistryFileDownloader(context)
        val parsers = RegistryFileParsers()

        emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_init)))

        for (plan in sourcePlans) {
            val primary = plan.first()
            var handled = false
            var lastError: Exception? = null

            for (source in plan) {
                try {
                    Log.i(tag, "Start source=${source.name} url=${source.url}")
                    emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_download, source.name)))
                    val downloaded = downloader.download(source)
                    Log.i(tag, "Downloaded source=${source.name} file=${downloaded.file.absolutePath} size=${downloaded.file.length()}")
                    emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_parse, source.name)))
                    val parsed = parsers.parse(source, downloaded.file)
                    emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_save, source.name)))
                    val batchId = persistence.saveSnapshot(
                        source = source,
                        sourceUrl = source.url,
                        fileName = downloaded.file.name,
                        fileSha256 = sha256(downloaded.file),
                        snapshotDateUtc = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),
                        fetchedAtUtc = java.time.Instant.now().toString(),
                        parsed = parsed
                    )
                    Log.i(tag, "Saved source=${source.name} batchId=$batchId")
                    downloaded.cleanup()
                    handled = true
                    break
                } catch (error: Exception) {
                    lastError = error
                    Log.e(tag, "Ingestion failed for source=${source.name}", error)
                }
            }

            completedPlans++
            if (handled) {
                emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_source, primary.name)))
            } else {
                val reason = lastError?.message ?: context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_no_fallback)
                emit(
                    StartupProgress(
                        percent(),
                        context.getString(
                            pl.syntaxdevteam.medstock.R.string.preloader_status_failed_source,
                            primary.name,
                            reason
                        )
                    )
                )
            }
        }

        schedule.markRunCompleted()
        emit(StartupProgress(100, context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_all)))
    }.flowOn(Dispatchers.IO)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            DigestInputStream(input, digest).use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (stream.read(buffer) != -1) Unit
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
