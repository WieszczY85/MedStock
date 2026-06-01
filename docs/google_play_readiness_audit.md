# Audyt gotowości MedStock do publikacji w Google Play

Data audytu: 2026-06-01
Repozytorium: `/workspace/MedStock_One`
Zakres: statyczna analiza konfiguracji Android/Gradle, manifestu, funkcji prywatności i backupu, porównanie z aktualnymi wymogami Google Play oraz uruchomienie lokalnych bramek walidacyjnych Gradle.

## Źródła wymogów Google użyte w audycie

- Google Play target API level requirement: https://developer.android.com/google/play/requirements/target-sdk
- Upload your app to the Play Console / Play App Signing / size requirements: https://developer.android.com/studio/publish/upload-bundle
- Play App Signing Help: https://support.google.com/googleplay/android-developer/answer/9842756
- Google Play Health Content and Services policy: https://support.google.com/googleplay/android-developer/answer/16679511
- Health apps declaration form guidance: https://support.google.com/googleplay/android-developer/answer/14738291
- Data safety section guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Permissions and APIs that Access Sensitive Information, including exact alarms and full-screen intents: https://support.google.com/googleplay/android-developer/answer/16558241
- 64-bit support requirement: https://developer.android.com/google/play/requirements/64-bit
- 16 KB page size requirement: https://developer.android.com/guide/practices/page-sizes

## Werdykt końcowy

**Aplikacja nie jest jeszcze gotowa do produkcyjnego dodania do Google Play.** Kod przechodzi debug build, lint i testy jednostkowe, a konfiguracja `targetSdk = 36` spełnia aktualny próg target API. Blokery publikacyjne są jednak realne: release bundle nie podpisuje się w obecnym środowisku, release ma wyłączone minifikację i shrink zasobów, wersja nadal wygląda jak snapshot, a dla aplikacji o lekach brakuje repozytoryjnie uchwyconych elementów compliance: polityki prywatności, jasnego medycznego disclaimera oraz przygotowania deklaracji Health/Data safety/permissions w Play Console.

## Najważniejsze ryzyka i decyzje

| Priorytet | Obszar | Status | Wniosek |
|---|---|---:|---|
| P0 | Release AAB i podpis | ❌ Bloker | `:app:bundleRelease` dochodzi do `:app:signReleaseBundle`, ale kończy się błędem, bo release signing nie ma danych keystore w repo/środowisku. Bez poprawnego AAB nie ma publikacji. |
| P0 | Health Apps policy | ❌ Bloker procesowy | Aplikacja zarządza lekami, dawkami, przypomnieniami i zapasami, więc wchodzi w kategorię Health/Medical, w praktyce „Medication and Treatment Management”. Trzeba uzupełnić Health apps declaration i dodać jasny disclaimer. |
| P0 | Prywatność / Data safety | ❌ Bloker procesowy | Aplikacja zapisuje listę leków, dawkowanie, przypomnienia i email konta w snapshocie Google Drive. Nie znaleziono polityki prywatności ani tekstu/linku w aplikacji. |
| P1 | Uprawnienia wrażliwe | ⚠️ Wysokie ryzyko review | Manifest deklaruje `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, `GET_ACCOUNTS`. Exact alarm i full-screen intent wymagają mocnego uzasadnienia i deklaracji/zgód. |
| P1 | Release hardening | ⚠️ Do poprawy | `isMinifyEnabled = false` i `isShrinkResources = false` zwiększają rozmiar oraz ułatwiają analizę aplikacji. To nie zawsze blokuje Play, ale jest słabe dla release. |
| P1 | Numeracja wersji | ⚠️ Do poprawy | `versionName = "0.7.0-R0.1-SNAPSHOT"` wygląda jak build przedprodukcyjny. Play przyjmie technicznie, ale biznesowo i review-wise wygląda niedojrzale. |
| P2 | 64-bit i 16 KB page size | ✅ OK według APK debug | W debug APK nie wykryto `.so`, więc według wytycznych Google aplikacja Kotlin/Java bez native libs spełnia 64-bit i 16 KB page-size od strony binarnej. Trzeba potwierdzić na finalnym release AAB. |
| P2 | Target API | ✅ OK | `targetSdk = 36`, czyli powyżej wymogu Android 15/API 35 dla nowych aplikacji i update’ów. |

## Porównanie wymogów Google Play z kodem

### 1. Target API i Android SDK

Google Play wymaga, aby nowe aplikacje i aktualizacje od 2025-08-31 targetowały Android 15/API 35 lub wyżej. Projekt spełnia ten wymóg: `compileSdk` używa Android 36.1, a `targetSdk = 36`.

Ocena: **zaliczone**.

### 2. Artefakt publikacyjny i podpisywanie

Google Play dla nowych aplikacji wymaga Play App Signing i uploadu podpisanego artefaktu, najczęściej Android App Bundle. Projekt ma konfigurację release signing opartą o `keystore.properties`, ale tylko jeśli plik istnieje. Gdy pliku nie ma, signing config pozostaje pusty, a `:app:bundleRelease` kończy się błędem na podpisywaniu.

Wynik lokalny:

```text
> Task :app:signReleaseBundle FAILED
Execution failed for task ':app:signReleaseBundle' ... NullPointerException
```

Ocena: **bloker**.

Rekomendacje:

1. Przygotować bezpieczny proces generowania/upload key poza repozytorium.
2. Nie commitować `keystore.properties`, ale dodać `keystore.properties.example` z wymaganymi kluczami.
3. Rozważyć fail-fast w Gradle: jeśli budowany jest release i brakuje keystore, zwracać czytelny błąd zamiast NPE.
4. Po skonfigurowaniu uruchomić `./gradlew --no-daemon :app:bundleRelease` i sprawdzić wygenerowany `.aab` w Play Console internal testing.

### 3. Health/Medical policy

Opis projektu mówi wprost, że aplikacja pomaga w ewidencji leków, kontrolowaniu zapasów, przypominaniu o dawkach i terminach ważności. To nie jest neutralna aplikacja narzędziowa; to aplikacja zdrowotna z funkcjami medication/treatment management. Google wymaga dla takich aplikacji deklaracji Health apps, polityki prywatności, a dla nieregulowanych aplikacji medycznych jasnego disclaimera, że aplikacja nie jest wyrobem medycznym i nie diagnozuje, nie leczy, nie zapobiega ani nie zapobiega chorobom; dodatkowo ma przypominać o konsultacji z pracownikiem ochrony zdrowia.

W repozytorium nie znaleziono tekstu privacy policy ani medycznego disclaimera (`rg "privacy|prywat|policy|polityka|disclaimer|medical device|wyrób medyczny|lekarz|healthcare|professional|konsult" .`).

Ocena: **bloker procesowy przed review**.

Rekomendacje:

1. W aplikacji dodać ekran/sekcję „Informacje prawne” lub „Prywatność i bezpieczeństwo”.
2. Dodać stringi EN/PL z disclaimerem, np. sens: „MedStock nie jest wyrobem medycznym, nie diagnozuje, nie leczy, nie zapobiega chorobom ani ich nie monitoruje w celu medycznym. W sprawach dawkowania, diagnozy i leczenia skonsultuj się z lekarzem/farmaceutą.”
3. Tę samą treść umieścić w opisie Google Play, nie tylko w aplikacji.
4. W Play Console zadeklarować kategorię zdrowotną zgodną z funkcjami: najpewniej `Medication and Treatment Management`.
5. Unikać marketingowych claimów typu „poprawia leczenie”, „zapobiega pominięciu dawki”, „bezpieczne dawkowanie”, jeśli nie ma podstaw regulacyjnych/klinicznych.

### 4. Data safety, dane użytkownika i polityka prywatności

Aplikacja tworzy snapshot backupu zawierający email konta, listę leków, moc, substancję czynną, stan, dawkowanie, progi alertów, godziny przypomnień, etykiety i powiązania leków z reminderami. Snapshot jest zapisywany lokalnie i wysyłany do Google Drive `appDataFolder` po autoryzacji użytkownika.

To oznacza co najmniej deklaracje Data safety dla:

- Health info / medication-related user data.
- Personal info / email address lub account identifiers.
- App activity/settings, jeśli ujmowane są preferencje.
- Data transmitted off-device do Google Drive po akcji/zgodzie użytkownika.

Ocena: **bloker procesowy**.

Rekomendacje:

1. Opublikować politykę prywatności jako aktywny publiczny URL HTML, nie PDF, bez geofencingu.
2. Dodać link/tekst polityki prywatności w aplikacji.
3. Opisać dokładnie: jakie dane są lokalne, co trafia do Google Drive, kiedy, w jakim celu, jak użytkownik wyłącza backup i jak usuwa dane.
4. W Data safety nie udawać „no data collected”, bo backup do Google Drive i pobieranie profilu Google oznaczają przepływy danych, które muszą być ujawnione zgodnie z interpretacją Google.
5. Rozważyć szyfrowanie snapshotu przed wysłaniem do Drive lub przynajmniej jasną komunikację, że backup jest przechowywany w prywatnej przestrzeni aplikacji na Google Drive i chroniony przez konto Google.

### 5. Uprawnienia i wrażliwe API

Manifest deklaruje szeroki zestaw uprawnień: internet, stan sieci, konto Google do Android 25, powiadomienia, exact alarms, odbiór boot completed, full-screen intent i wibracje.

Największe ryzyka:

- `USE_EXACT_ALARM`: Google traktuje to jako wysoko ograniczone uprawnienie dla aplikacji, których podstawową funkcją są alarmy/timery/kalendarze. MedStock ma przypomnienia leków jako funkcję krytyczną, ale nie jest czystym budzikiem. To wymaga ostrożnego opisu core functionality i deklaracji. Jeżeli Play uzna, że use case nie pasuje, publikacja może zostać zablokowana.
- `SCHEDULE_EXACT_ALARM`: alternatywa z zgodą użytkownika; nadal wymaga UX fallbacku.
- `USE_FULL_SCREEN_INTENT`: dla Android 14+ auto-grant tylko dla alarmów i połączeń. Aplikacja ma ekran dzwonienia przypomnienia o leku, więc argument istnieje, ale trzeba mieć jasny UX zgody i deklarację w Play Console.
- `GET_ACCOUNTS` z `maxSdkVersion=25`: legacy ograniczone do starych urządzeń; minSdk to 31, więc to uprawnienie jest obecnie martwe i powinno zostać usunięte dla czystości manifestu.

Ocena: **wysokie ryzyko review, częściowo do uproszczenia**.

Rekomendacje:

1. Usunąć `GET_ACCOUNTS`, bo `minSdk = 31` czyni `maxSdkVersion=25` bezużytecznym.
2. Zdecydować, czy naprawdę potrzebne są oba `USE_EXACT_ALARM` i `SCHEDULE_EXACT_ALARM`.
3. Jeśli zostaje `USE_EXACT_ALARM`, przygotować w Play Console opis core functionality: terminowe alarmy dawki leku jako podstawowa funkcja bezpieczeństwa użytkownika.
4. W opisie sklepu jawnie promować przypomnienia leków jako core feature, bo Google wymaga spójności między listingiem a uprawnieniami.
5. Utrzymać fallback w aplikacji, gdy użytkownik nie udzieli zgód na powiadomienia/full-screen/exact alarm.

### 6. 64-bit i 16 KB page-size

Debug APK nie zawiera natywnych bibliotek `.so`. Google wskazuje, że aplikacje używające wyłącznie Java/Kotlin, wraz z bibliotekami bez native code, wspierają 64-bit i 16 KB page-size. Z tego względu obecny APK debug wygląda bezpiecznie. Finalnie należy powtórzyć kontrolę na release AAB/APK wygenerowanym przez Play Console.

Wykonana kontrola:

```bash
zipinfo -1 app/build/outputs/apk/debug/app-debug.apk | rg '\.so$|^lib/' || true
```

Wynik: brak wpisów `.so`.

Ocena: **zaliczone w zakresie debug APK; do potwierdzenia na finalnym release**.

### 7. Rozmiar aplikacji

Debug APK ma około 46 MB. To daleko poniżej limitów Google Play, ale release z włączonym minify/shrink prawdopodobnie będzie mniejszy. Biblioteki Apache POI są ciężkie; jeżeli rozmiar AAB urośnie, należy rozważyć optymalizację parserów lub dynamic delivery.

Ocena: **bez blokera**.

### 8. Backup i retencja danych

`backup_rules.xml` i `data_extraction_rules.xml` obejmują plik `drive_backup/medstock_medications_backup.json`. To oznacza, że backup aplikacji może przenosić właśnie snapshot danych leków. Jest to spójne funkcjonalnie, ale wymaga przejrzystego disclosure w privacy policy i Data safety. Użytkownik musi rozumieć, że w backupie znajdują się dane o lekach.

Ocena: **wymaga disclosure**.

### 9. Jakość release i hardening

Release ma wyłączone `isMinifyEnabled` i `isShrinkResources`. To nie musi być automatyczny blocker Google Play, ale dla aplikacji zdrowotnej operującej na wrażliwych danych to słaby standard produkcyjny.

Ocena: **do poprawy przed produkcją**.

Rekomendacje:

1. Włączyć R8 minification dla release.
2. Włączyć shrink resources.
3. Dodać reguły ProGuard dla bibliotek, które tego wymagają.
4. Przetestować import rejestrów i Google Drive po minifikacji.

## Walidacja wykonana lokalnie

| Komenda | Wynik | Uwagi |
|---|---:|---|
| `chmod +x gradlew` | ✅ | Gradle wrapper jest wykonywalny. |
| `git fetch --depth=1000000 --all --tags --prune` | ✅ | Zgodnie z instrukcją użytkownika. |
| Bootstrap Android SDK z AGENTS.md | ✅ | Zainstalowano/zweryfikowano platform-tools, Android 36.1 i build-tools. |
| `./gradlew --no-daemon :app:assembleDebug` | ✅ | Build debug zakończony sukcesem. |
| `./gradlew --no-daemon :app:lintDebug` | ✅ | Lint debug zakończony sukcesem. |
| `./gradlew test --console=plain --no-daemon` | ✅ | Testy jednostkowe zakończone sukcesem. |
| `./gradlew --no-daemon :app:bundleRelease --console=plain` | ❌ | Build doszedł do podpisywania i padł na `:app:signReleaseBundle`; release signing nie jest gotowy. |
| `zipinfo -1 app/build/outputs/apk/debug/app-debug.apk | rg '\.so$|^lib/' || true` | ✅ | Brak native libs w APK debug. |

## Minimalny plan naprawczy przed wrzuceniem do Google Play

1. **Napraw release signing i AAB**
   - Przygotuj upload keystore poza repo.
   - Dodaj `keystore.properties.example`.
   - Dodaj czytelny fail-fast dla release bez keystore.
   - Wygeneruj finalny AAB: `./gradlew --no-daemon :app:bundleRelease`.

2. **Dodaj compliance health/privacy w aplikacji i store listing**
   - Ekran/link polityki prywatności.
   - EN/PL medyczny disclaimer.
   - Informacja o Google Drive backupie i danych leków.
   - Opis usuwania danych i wyłączania backupu.

3. **Przygotuj deklaracje Play Console**
   - Health apps declaration: Medication and Treatment Management.
   - Data safety: health info, personal info/email, backup/cloud transfer, optionality.
   - Permissions declarations: exact alarm/full-screen intent, jeśli zostają.

4. **Uprość manifest**
   - Usuń martwe `GET_ACCOUNTS`.
   - Zweryfikuj, czy potrzebne są jednocześnie `USE_EXACT_ALARM` i `SCHEDULE_EXACT_ALARM`.

5. **Utwardź release**
   - Włącz minify i shrink resources.
   - Przetestuj krytyczne ścieżki po R8.

6. **Zmień wersję na produkcyjną**
   - Nie publikuj `0.7.0-R0.1-SNAPSHOT` jako produkcji.
   - Ustal semver/release naming, np. `1.0.0` albo `0.7.0` bez snapshot, zależnie od strategii.

## Szczera konkluzja

Założenie „wszystkie funkcje są wdrożone, więc aplikacja jest gotowa do sklepu” jest fałszywe. Funkcjonalna kompletność to tylko jeden wymiar. Dla Google Play równie ważne są: podpisywanie, deklaracje danych, polityka zdrowotna, zgodność uprawnień z listingiem, finalny AAB i proces testów. Na dziś projekt jest dobry jako build debug i kandydat do dalszego przygotowania, ale nie jako paczka gotowa do produkcyjnego review.
