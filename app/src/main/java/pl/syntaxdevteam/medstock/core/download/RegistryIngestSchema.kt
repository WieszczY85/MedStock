package pl.syntaxdevteam.medstock.core.download

internal object RegistryIngestSchema {
    const val VERSION = 8

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
        CREATE TABLE IF NOT EXISTS registry_rpl_column_dictionary (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            column_key TEXT NOT NULL UNIQUE,
            first_seen_batch_id INTEGER NOT NULL,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(first_seen_batch_id) REFERENCES registry_import_batch(id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS registry_rpl_row (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            batch_id INTEGER NOT NULL,
            source_row_number INTEGER NOT NULL,
            source_entity_key TEXT,
            row_hash TEXT,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(batch_id) REFERENCES registry_import_batch(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_rpl_row_batch_row ON registry_rpl_row(batch_id, source_row_number)",
        "CREATE INDEX IF NOT EXISTS ix_registry_rpl_row_entity ON registry_rpl_row(source_entity_key)",
        """
        CREATE TABLE IF NOT EXISTS registry_rpl_cell (
            row_id INTEGER NOT NULL,
            column_id INTEGER NOT NULL,
            value_text TEXT,
            PRIMARY KEY (row_id, column_id),
            FOREIGN KEY(row_id) REFERENCES registry_rpl_row(id) ON DELETE CASCADE,
            FOREIGN KEY(column_id) REFERENCES registry_rpl_column_dictionary(id) ON DELETE RESTRICT
        )
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS registry_ra_column_dictionary (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            column_key TEXT NOT NULL UNIQUE,
            first_seen_batch_id INTEGER NOT NULL,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(first_seen_batch_id) REFERENCES registry_import_batch(id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS registry_ra_row (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            batch_id INTEGER NOT NULL,
            source_row_number INTEGER NOT NULL,
            source_entity_key TEXT,
            row_hash TEXT,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(batch_id) REFERENCES registry_import_batch(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_ra_row_batch_row ON registry_ra_row(batch_id, source_row_number)",
        "CREATE INDEX IF NOT EXISTS ix_registry_ra_row_entity ON registry_ra_row(source_entity_key)",
        """
        CREATE TABLE IF NOT EXISTS registry_ra_cell (
            row_id INTEGER NOT NULL,
            column_id INTEGER NOT NULL,
            value_text TEXT,
            PRIMARY KEY (row_id, column_id),
            FOREIGN KEY(row_id) REFERENCES registry_ra_row(id) ON DELETE CASCADE,
            FOREIGN KEY(column_id) REFERENCES registry_ra_column_dictionary(id) ON DELETE RESTRICT
        )
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS registry_rdg_column_dictionary (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            column_key TEXT NOT NULL UNIQUE,
            first_seen_batch_id INTEGER NOT NULL,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(first_seen_batch_id) REFERENCES registry_import_batch(id) ON DELETE RESTRICT
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS registry_rdg_row (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            batch_id INTEGER NOT NULL,
            source_row_number INTEGER NOT NULL,
            source_entity_key TEXT,
            row_hash TEXT,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(batch_id) REFERENCES registry_import_batch(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_rdg_row_batch_row ON registry_rdg_row(batch_id, source_row_number)",
        "CREATE INDEX IF NOT EXISTS ix_registry_rdg_row_entity ON registry_rdg_row(source_entity_key)",
        """
        CREATE TABLE IF NOT EXISTS registry_rdg_cell (
            row_id INTEGER NOT NULL,
            column_id INTEGER NOT NULL,
            value_text TEXT,
            PRIMARY KEY (row_id, column_id),
            FOREIGN KEY(row_id) REFERENCES registry_rdg_row(id) ON DELETE CASCADE,
            FOREIGN KEY(column_id) REFERENCES registry_rdg_column_dictionary(id) ON DELETE RESTRICT
        )
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS registry_rpl_snapshot (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            batch_id INTEGER NOT NULL,
            source_row_number INTEGER NOT NULL,
            source_entity_key TEXT,
            identyfikator_produktu_leczniczego TEXT,
            nazwa_produktu_leczniczego TEXT,
            droga_podania_gatunek_tkanka_okres_karencji TEXT,
            moc TEXT,
            postac_farmaceutyczna TEXT,
            podmiot_odpowiedzialny TEXT,
            kod_ean TEXT,
            opakowanie TEXT,
            substancja_czynna TEXT,
            kraj_wytworcy TEXT,
            ulotka TEXT,
            charakterystyka TEXT,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(batch_id) REFERENCES registry_import_batch(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_rpl_snapshot_batch_row ON registry_rpl_snapshot(batch_id, source_row_number)",
        "CREATE INDEX IF NOT EXISTS ix_registry_rpl_snapshot_nazwa ON registry_rpl_snapshot(nazwa_produktu_leczniczego)",
        "CREATE INDEX IF NOT EXISTS ix_registry_rpl_snapshot_ean ON registry_rpl_snapshot(kod_ean)",
        "CREATE INDEX IF NOT EXISTS ix_registry_rpl_snapshot_entity ON registry_rpl_snapshot(source_entity_key)",

        """
        CREATE TABLE IF NOT EXISTS registry_ra_snapshot (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            batch_id INTEGER NOT NULL,
            source_row_number INTEGER NOT NULL,
            source_entity_key TEXT,
            nazwa_apteki TEXT,
            stan_apteki TEXT,
            wojewodztwo TEXT,
            powiat TEXT,
            gmina TEXT,
            typ_ulicy TEXT,
            nazwa_ulicy TEXT,
            numer_budynku TEXT,
            numer_lokalu TEXT,
            miejscowosc TEXT,
            kod_pocztowy TEXT,
            telefon TEXT,
            fax TEXT,
            email TEXT,
            adres_www TEXT,
            czy_sprzedaz_wysylkowa TEXT,
            godziny_otwarcia_poniedzialek TEXT,
            godziny_otwarcia_wtorek TEXT,
            godziny_otwarcia_sroda TEXT,
            godziny_otwarcia_czwartek TEXT,
            godziny_otwarcia_piatek TEXT,
            godziny_otwarcia_sobota TEXT,
            godziny_otwarcia_niedziela_handlowa TEXT,
            godziny_otwarcia_niedziela_niehandlowa TEXT,
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY(batch_id) REFERENCES registry_import_batch(id) ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_ra_snapshot_batch_row ON registry_ra_snapshot(batch_id, source_row_number)",
        "CREATE INDEX IF NOT EXISTS ix_registry_ra_snapshot_nazwa ON registry_ra_snapshot(nazwa_apteki)",
        "CREATE INDEX IF NOT EXISTS ix_registry_ra_snapshot_entity ON registry_ra_snapshot(source_entity_key)",

        """
        CREATE TABLE IF NOT EXISTS user_medication (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            strength TEXT NOT NULL DEFAULT '',
            active_substance TEXT NOT NULL DEFAULT '',
            package_size TEXT NOT NULL DEFAULT '',
            unit TEXT NOT NULL DEFAULT '',
            current_stock INTEGER NOT NULL DEFAULT 0,
            dosage TEXT NOT NULL DEFAULT '',
            alert_days INTEGER NOT NULL DEFAULT 0,
            last_stock_update_utc TEXT NOT NULL DEFAULT (datetime('now')),
            created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at_utc TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS ix_user_medication_name ON user_medication(name)"
    )
}
