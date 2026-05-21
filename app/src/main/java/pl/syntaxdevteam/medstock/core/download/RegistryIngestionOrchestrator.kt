package pl.syntaxdevteam.medstock.core.download

import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RegistryIngestionOrchestrator(
    private val downloader: TemporaryRegistryFileDownloader,
    private val parsers: RegistryFileParsers,
    private val persistence: RegistrySnapshotPersistence,
    private val clock: Clock = Clock.systemUTC()
) {

    fun ingest(source: RegistryFileSource): Long {
        val downloaded = downloader.download(source)
        return try {
            ingestFile(source, downloaded.file, source.url)
        } finally {
            downloaded.cleanup()
        }
    }

    fun ingestFile(source: RegistryFileSource, file: File, sourceUrl: String = source.url): Long {
        val parsed = parsers.parse(source, file)
        val now = Instant.now(clock)
        val snapshotDateUtc = now.atZone(ZoneOffset.UTC).toLocalDate().toString()
        return persistence.saveSnapshot(
            source = source,
            sourceUrl = sourceUrl,
            fileName = file.name,
            fileSha256 = sha256(file),
            snapshotDateUtc = snapshotDateUtc,
            fetchedAtUtc = now.toString(),
            parsed = parsed
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
