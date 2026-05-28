package pl.syntaxdevteam.medstock.core.download

internal object RegistryIngestSchema {
    const val VERSION = 1

    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS rdg (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            data_snapshot TEXT NOT NULL,
            source_row_number INTEGER NOT NULL,
            source_entity_key TEXT,
            raw_payload TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_rdg_snapshot_row ON rdg(data_snapshot, source_row_number)",
        "CREATE INDEX IF NOT EXISTS ix_rdg_snapshot ON rdg(data_snapshot)",
        "CREATE INDEX IF NOT EXISTS ix_rdg_entity ON rdg(source_entity_key)",

        """
        CREATE TABLE IF NOT EXISTS rpl (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            data_snapshot TEXT NOT NULL,
            identyfikator_produktu TEXT,
            nazwa_produktu TEXT,
            droga_podania TEXT,
            moc TEXT,
            postac_farmaceutyczna TEXT,
            podmiot_odpowiedzialny TEXT,
            kod_ean TEXT,
            opakowanie TEXT,
            substancja_czynna TEXT,
            kraj_wytworcy TEXT,
            ulotka TEXT,
            charakterystyka TEXT
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_rpl_snapshot_id ON rpl(data_snapshot, identyfikator_produktu)",
        "CREATE INDEX IF NOT EXISTS ix_rpl_snapshot ON rpl(data_snapshot)",
        "CREATE INDEX IF NOT EXISTS ix_rpl_nazwa ON rpl(nazwa_produktu)",
        "CREATE INDEX IF NOT EXISTS ix_rpl_substancja ON rpl(substancja_czynna)",
        "CREATE INDEX IF NOT EXISTS ix_rpl_snapshot_nazwa ON rpl(data_snapshot, nazwa_produktu)",

        """
        CREATE TABLE IF NOT EXISTS ra (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            data_snapshot TEXT NOT NULL,
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
            godziny_otwarcia_niedziela_niehandlowa TEXT
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_ra_snapshot_entity ON ra(data_snapshot, source_entity_key)",
        "CREATE INDEX IF NOT EXISTS ix_ra_snapshot ON ra(data_snapshot)",
        "CREATE INDEX IF NOT EXISTS ix_ra_nazwa ON ra(nazwa_apteki)",
        "CREATE INDEX IF NOT EXISTS ix_ra_miejscowosc ON ra(miejscowosc)",
        "CREATE INDEX IF NOT EXISTS ix_ra_snapshot_miejscowosc ON ra(data_snapshot, miejscowosc)",

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
