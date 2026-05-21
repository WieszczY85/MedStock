# Profil źródeł RA/RPL/RDG (pobrane 2026-05-21 UTC)

## Co zostało pobrane

- RDG XML: `https://rdg.ezdrowie.gov.pl/Decision/DownloadPublicXml`
- RPL CSV/XLSX:
  - `https://rejestry.ezdrowie.gov.pl/api/rpl/medicinal-products/public-pl-report/get-csv`
  - `https://rejestry.ezdrowie.gov.pl/api/rpl/medicinal-products/public-pl-report/get-xlsx`
- RA CSV/XLS:
  - `https://rejestry.ezdrowie.gov.pl/api/ra/filegenerator/getcsv`
  - `https://rejestry.ezdrowie.gov.pl/api/ra/filegenerator/getxls`

## Wyniki inspekcji

### RPL
- CSV jest rozdzielany średnikiem (`;`) i zawiera **33 kolumny**.
- XLSX (arkusz `Lista Produktow Leczniczych`) także zawiera **33 kolumny** i odpowiada nagłówkom z CSV.
- Przykładowy klucz encji: `Identyfikator Produktu Leczniczego`.

### RA
- CSV jest rozdzielany pionową kreską (`|`) i zawiera **76 kolumn** (BOM UTF-8 w pierwszym nagłówku).
- XLS (arkusz `Rejestr Aptek`) zawiera **76 kolumn** zgodnych logicznie z CSV.
- Przykładowy klucz encji: `identyfikator_apteki`.

### RDG
- XML ma korzeń `Decyzje`.
- Powtarzalna encja to `Decyzja` (zawiera m.in. `DataDecyzji`, `NumerDecyzji`, `NumerSprawy`, `LinkDoPobraniaDecyzji`, sekcje `Produkt`, `Seria`, `Przyczyna`).
- Przykładowy klucz encji: `NumerDecyzji`.

## Wniosek architektoniczny

Wymaganie „zapis 1:1” nie powinno być realizowane jako 3 osobne, sztywne tabele domenowe na tym etapie,
bo formaty publicznych rejestrów zmieniają się w czasie (dodawane/zmieniane kolumny). Najbezpieczniejsza baza
pod parsery to warstwa **staging EAV** (batch -> row -> cell + dictionary kolumn), opisana w:

- `docs/registry_ingest_sqlite_schema.sql`

Dopiero później warto budować stabilne tabele projekcyjne pod UI/wyszukiwanie.


## Strategia antyduplikacyjna (aktualizacja dzienna)

Jeśli chcesz uniknąć dopisywania istniejących danych, **musisz** rozdzielić:
- datę pobrania (`fetched_at_utc`) i
- datę logicznego wydania wsadu (`snapshot_date_utc`, np. `2026-05-21`).

Dlatego w schemacie dodano:
- unikalność `source_code + snapshot_date_utc` (1 wsad dziennie na źródło),
- unikalność `source_code + file_sha256` (ochrona przed identycznym plikiem),
- opcjonalny `row_hash` do szybkiego porównania zmian między dniami.

W praktyce zapis powinien iść przez `UPSERT` po `(source_code, snapshot_date_utc)` i podmianę danych batcha,
zamiast ślepego `INSERT`.
