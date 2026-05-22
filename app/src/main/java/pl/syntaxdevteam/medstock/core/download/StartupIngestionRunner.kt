package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class StartupProgress(
    val progressPercent: Int,
    val message: String
)

class StartupIngestionRunner(private val context: Context) {
    private val tag = "StartupIngestionRunner"

    fun run(): Flow<StartupProgress> = flow {
        val sources = RegistryFileSource.entries
        val totalSteps = sources.size * 3
        var completedSteps = 0

        fun percent(): Int = ((completedSteps * 100f) / totalSteps).toInt().coerceIn(0, 100)

        val dbHelper = RegistryIngestDatabaseHelper(context)
        val persistence = RegistrySnapshotPersistence(dbHelper.writableDatabase)
        val downloader = TemporaryRegistryFileDownloader(context)
        val parsers = RegistryFileParsers()

        emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_init)))

        for (source in sources) {
            try {
                Log.i(tag, "Start source=${source.name} url=${source.url}")
                emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_download, source.name)))
                val downloaded = downloader.download(source)
                Log.i(tag, "Downloaded source=${source.name} file=${downloaded.file.absolutePath} size=${downloaded.file.length()}")
                completedSteps++
                emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_parse, source.name)))
                val parsed = parsers.parse(source, downloaded.file)
                completedSteps++
                emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_save, source.name)))
                val batchId = persistence.saveSnapshot(
                    source = source,
                    sourceUrl = source.url,
                    fileName = downloaded.file.name,
                    fileSha256 = java.security.MessageDigest.getInstance("SHA-256").digest(downloaded.file.readBytes())
                        .joinToString("") { "%02x".format(it) },
                    snapshotDateUtc = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),
                    fetchedAtUtc = java.time.Instant.now().toString(),
                    parsed = parsed
                )
                Log.i(tag, "Saved source=${source.name} batchId=$batchId")
                downloaded.cleanup()
                completedSteps++
                emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_source, source.name)))
            } catch (error: Exception) {
                Log.e(tag, "Ingestion failed for source=${source.name}", error)
                completedSteps += 3
                emit(
                    StartupProgress(
                        percent(),
                        context.getString(
                            pl.syntaxdevteam.medstock.R.string.preloader_status_failed_source,
                            source.name,
                            error.message ?: context.getString(pl.syntaxdevteam.medstock.R.string.common_unknown_error)
                        )
                    )
                )
            }
        }

        emit(StartupProgress(100, context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_all)))
    }.flowOn(Dispatchers.IO)
}
