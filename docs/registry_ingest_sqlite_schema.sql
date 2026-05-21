-- Proponowany schemat SQLite do zapisu danych źródłowych RA/RPL/RDG.
-- Założenie: zapis surowy 1:1 (staging), a dopiero potem opcjonalna normalizacja.
-- Ważne: źródła aktualizują się cyklicznie, więc batch musi mieć klucz daty publikacji/snapshotu.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS registry_import_batch (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_code TEXT NOT NULL,                  -- RA_CSV, RA_XLS, RPL_CSV, RPL_XLSX, RDG_XML
    snapshot_date_utc TEXT NOT NULL,            -- YYYY-MM-DD; logiczna data wydania paczki danych
    source_url TEXT NOT NULL,
    file_name TEXT,
    source_last_modified_utc TEXT,              -- z nagłówka HTTP Last-Modified (jeśli dostępny)
    source_etag TEXT,                            -- z nagłówka HTTP ETag (jeśli dostępny)
    file_sha256 TEXT NOT NULL,
    fetched_at_utc TEXT NOT NULL,               -- ISO-8601 czas pobrania
    parser_version TEXT NOT NULL,
    record_count INTEGER NOT NULL DEFAULT 0,
    created_at_utc TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 1) Ochrona przed powtórnym zapisem identycznego pliku.
CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_import_batch_sha
    ON registry_import_batch(source_code, file_sha256);

-- 2) Ochrona przed dublowaniem wsadu "dziennego" dla źródła.
CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_import_batch_source_day
    ON registry_import_batch(source_code, snapshot_date_utc);

CREATE TABLE IF NOT EXISTS registry_column_dictionary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_code TEXT NOT NULL,
    column_key TEXT NOT NULL,                   -- np. nazwa kolumny CSV/XLS lub ścieżka tagu XML
    first_seen_batch_id INTEGER NOT NULL,
    created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY(first_seen_batch_id) REFERENCES registry_import_batch(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_column_dictionary
    ON registry_column_dictionary(source_code, column_key);

CREATE TABLE IF NOT EXISTS registry_row (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_id INTEGER NOT NULL,
    source_row_number INTEGER NOT NULL,
    source_entity_key TEXT,                     -- np. identyfikator_apteki, identyfikator produktu, numer decyzji
    row_hash TEXT,                              -- opcjonalnie hash wartości wiersza do szybkiego diffowania
    created_at_utc TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY(batch_id) REFERENCES registry_import_batch(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_row_batch_row
    ON registry_row(batch_id, source_row_number);

CREATE INDEX IF NOT EXISTS ix_registry_row_entity
    ON registry_row(source_entity_key);

CREATE INDEX IF NOT EXISTS ix_registry_row_hash
    ON registry_row(row_hash);

CREATE TABLE IF NOT EXISTS registry_cell (
    row_id INTEGER NOT NULL,
    column_id INTEGER NOT NULL,
    value_text TEXT,
    PRIMARY KEY (row_id, column_id),
    FOREIGN KEY(row_id) REFERENCES registry_row(id) ON DELETE CASCADE,
    FOREIGN KEY(column_id) REFERENCES registry_column_dictionary(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_registry_cell_column
    ON registry_cell(column_id);

-- Widok pomocniczy: szybkie odczyty klucz=>wartość dla konkretnego wiersza.
CREATE VIEW IF NOT EXISTS registry_row_kv AS
SELECT
    rr.id AS row_id,
    rib.source_code,
    rib.snapshot_date_utc,
    rr.source_row_number,
    rr.source_entity_key,
    rcd.column_key,
    rc.value_text
FROM registry_row rr
JOIN registry_import_batch rib ON rib.id = rr.batch_id
JOIN registry_cell rc ON rc.row_id = rr.id
JOIN registry_column_dictionary rcd ON rcd.id = rc.column_id;

-- Przykładowy wzorzec zapisu batcha z nadpisaniem dziennym (SQLite UPSERT):
-- INSERT INTO registry_import_batch(source_code, snapshot_date_utc, source_url, file_sha256, fetched_at_utc, parser_version, record_count)
-- VALUES (?, ?, ?, ?, ?, ?, ?)
-- ON CONFLICT(source_code, snapshot_date_utc) DO UPDATE SET
--   source_url = excluded.source_url,
--   file_sha256 = excluded.file_sha256,
--   fetched_at_utc = excluded.fetched_at_utc,
--   parser_version = excluded.parser_version,
--   record_count = excluded.record_count;
