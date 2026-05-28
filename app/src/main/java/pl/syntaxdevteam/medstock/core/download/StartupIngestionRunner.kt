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
    val message: String,
    val currentTask: Int,
    val totalTasks: Int,
    val isLongRunning: Boolean = false
)

class StartupIngestionRunner(private val context: Context) {
    private val tag = "StartupIngestionRunner"

    fun run(force: Boolean = false): Flow<StartupProgress> = flow {
        val schedule = StartupIngestionSchedule(context)
        if (!force && !schedule.shouldRunNow()) {
            emit(StartupProgress(100, context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_all), 1, 1))
            return@flow
        }

        val sourcePlans = listOf(
            listOf(RegistryFileSource.RDG_XML),
            listOf(RegistryFileSource.RPL_XLSX, RegistryFileSource.RPL_CSV),
            listOf(RegistryFileSource.RA_CSV, RegistryFileSource.RA_XLS)
        )
        val totalTasks = 1 + (sourcePlans.size * 4)
        var completedTasks = 0

        fun percent(): Int = ((completedTasks * 100f) / totalTasks).toInt().coerceIn(0, 100)
        suspend fun emitProgress(message: String, isLongRunning: Boolean = false) {
            val currentTask = (completedTasks + 1).coerceAtMost(totalTasks)
            emit(StartupProgress(percent(), message, currentTask, totalTasks, isLongRunning))
        }

        val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)
        val persistence = RegistrySnapshotPersistence(dbHelper.writableDatabase)
        val downloader = TemporaryRegistryFileDownloader(context)
        val parsers = RegistryFileParsers()

        emitProgress(context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_init))
        completedTasks++

        var allPlansHandled = true

        for (plan in sourcePlans) {
            val primary = plan.first()
            var handled = false
            var lastError: Exception? = null

            for (source in plan) {
                try {
                    Log.i(tag, "Start source=${source.name} url=${source.url}")
                    emitProgress(context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_download, source.name))
                    val downloaded = downloader.download(source)
                    Log.i(tag, "Downloaded source=${source.name} file=${downloaded.file.absolutePath} size=${downloaded.file.length()}")
                    completedTasks++
                    emitProgress(
                        context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_parse, source.name),
                        isLongRunning = source == RegistryFileSource.RPL_XLSX
                    )
                    val parsed = parsers.parse(source, downloaded.file)
                    completedTasks++
                    emitProgress(context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_save, source.name))
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
                    completedTasks++
                    handled = true
                    break
                } catch (error: Exception) {
                    lastError = error
                    Log.e(tag, "Ingestion failed for source=${source.name}", error)
                }
            }

            if (handled) {
                emitProgress(context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_source, primary.name))
            } else {
                allPlansHandled = false
                val reason = lastError?.message ?: context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_no_fallback)
                emitProgress(
                    context.getString(
                        pl.syntaxdevteam.medstock.R.string.preloader_status_failed_source,
                        primary.name,
                        reason
                    )
                )
            }
            completedTasks++
        }

        if (allPlansHandled) {
            schedule.markRunCompleted()
            emit(StartupProgress(100, context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_all), totalTasks, totalTasks))
        } else {
            emit(StartupProgress(100, context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_partial_completion), totalTasks, totalTasks))
        }
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
