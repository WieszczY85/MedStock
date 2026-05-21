package pl.syntaxdevteam.medstock.core.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class StartupProgress(
    val progressPercent: Int,
    val message: String
)

class StartupIngestionRunner(private val context: Context) {

    fun run(): Flow<StartupProgress> = flow {
        val sources = RegistryFileSource.entries
        val totalSteps = sources.size * 3
        var completedSteps = 0

        fun percent(): Int = ((completedSteps * 100f) / totalSteps).toInt().coerceIn(0, 100)

        val dbHelper = RegistryIngestDatabaseHelper(context)
        val persistence = RegistrySnapshotPersistence(dbHelper.writableDatabase)
        val downloader = TemporaryRegistryFileDownloader(context)
        val parsers = RegistryFileParsers()
        val orchestrator = RegistryIngestionOrchestrator(downloader, parsers, persistence)

        emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_init)))

        for (source in sources) {
            emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_download, source.name)))
            val downloaded = downloader.download(source)
            completedSteps++
            emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_parse, source.name)))
            val parsed = parsers.parse(source, downloaded.file)
            completedSteps++
            emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_save, source.name)))
            persistence.saveSnapshot(
                source = source,
                sourceUrl = source.url,
                fileName = downloaded.file.name,
                fileSha256 = java.security.MessageDigest.getInstance("SHA-256").digest(downloaded.file.readBytes())
                    .joinToString("") { "%02x".format(it) },
                snapshotDateUtc = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),
                fetchedAtUtc = java.time.Instant.now().toString(),
                parsed = parsed
            )
            downloaded.cleanup()
            completedSteps++
            emit(StartupProgress(percent(), context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_source, source.name)))
        }

        emit(StartupProgress(100, context.getString(pl.syntaxdevteam.medstock.R.string.preloader_status_done_all)))
    }.flowOn(Dispatchers.IO)
}
