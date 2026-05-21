package pl.syntaxdevteam.medstock.core.download

internal object RegistryIngestSchema {
    const val VERSION = 1

    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS registry_import_batch (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_code TEXT NOT NULL,
            snapshot_date_utc TEXT NOT NULL,
            source_url TEXT NOT NULL,
            file_name TEXT,
            source_last_modified_utc TEXT,
            source_etag TEXT,
            file_sha256 TEXT NOT NULL,
            fetched_at_utc TEXT NOT NULL,
            parser_version TEXT NOT NULL,
            record_count INTEGER NOT NULL DEFAULT 0,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_import_batch_sha ON registry_import_batch(source_code, file_sha256)",
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_import_batch_source_day ON registry_import_batch(source_code, snapshot_date_utc)",
        """
        CREATE TABLE IF NOT EXISTS registry_column_dictionary (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_code TEXT NOT NULL,
            column_key TEXT NOT NULL,
            first_seen_batch_id INTEGER NOT NULL,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(first_seen_batch_id) REFERENCES registry_import_batch(id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_column_dictionary ON registry_column_dictionary(source_code, column_key)",
        """
        CREATE TABLE IF NOT EXISTS registry_row (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            batch_id INTEGER NOT NULL,
            source_row_number INTEGER NOT NULL,
            source_entity_key TEXT,
            row_hash TEXT,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(batch_id) REFERENCES registry_import_batch(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_row_batch_row ON registry_row(batch_id, source_row_number)",
        "CREATE INDEX IF NOT EXISTS ix_registry_row_entity ON registry_row(source_entity_key)",
        "CREATE INDEX IF NOT EXISTS ix_registry_row_hash ON registry_row(row_hash)",
        """
        CREATE TABLE IF NOT EXISTS registry_cell (
            row_id INTEGER NOT NULL,
            column_id INTEGER NOT NULL,
            value_text TEXT,
            PRIMARY KEY (row_id, column_id),
            FOREIGN KEY(row_id) REFERENCES registry_row(id) ON DELETE CASCADE,
            FOREIGN KEY(column_id) REFERENCES registry_column_dictionary(id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS ix_registry_cell_column ON registry_cell(column_id)"
    )
}
