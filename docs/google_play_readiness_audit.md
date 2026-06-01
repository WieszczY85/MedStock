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

**Aplikacja nie jest jeszcze gotowa do produkcyjnego dodania do Google Play.** Kod przechodzi debug build, lint i testy jednostkowe, a konfiguracja `targetSdk = 36` spełnia aktualny próg target API. Ważna korekta pozycjonowania: MedStock należy opisywać jako **menedżer domowych zasobów produktów**, w którym konkretną obsługiwaną kategorią są produkty lecznicze, a nie jako aplikację doradztwa medycznego. To jest lepsze i zgodne z README, ale nie usuwa obowiązków Google Play: aplikacja nadal przetwarza dane o produktach leczniczych, dawkowaniu wpisanym przez użytkownika oraz alertach terminów ważności i kończących się zapasów, więc wymaga polityki prywatności, ostrożnego disclaimera oraz deklaracji Health/Data safety/permissions w Play Console. Blokery publikacyjne pozostają realne: release bundle nie podpisuje się w obecnym środowisku, release ma wyłączone minifikację i shrink zasobów, a wersja nadal wygląda jak snapshot.

## Najważniejsze ryzyka i decyzje

| Priorytet | Obszar | Status | Wniosek |
|---|---|---:|---|
| P0 | Release AAB i podpis | ❌ Bloker | `:app:bundleRelease` dochodzi do `:app:signReleaseBundle`, ale kończy się błędem, bo release signing nie ma danych keystore w repo/środowisku. Bez poprawnego AAB nie ma publikacji. |
| P0 | Health Apps policy | ⚠️ Częściowo domknięte | Aplikacja jest menedżerem domowych zasobów produktów leczniczych i ma teraz w aplikacji oraz dokumentacji jasny disclaimer. Nadal trzeba wypełnić Health apps declaration w Play Console zgodnie z opisem funkcji. |
| P0 | Prywatność / Data safety | ⚠️ Domknięte repozytoryjnie, wymaga publikacji URL | Dodano politykę prywatności, draft deklaracji Data safety oraz in-app disclosure w Ustawieniach i ekranie Konta. Przed wysyłką do Play trzeba opublikować politykę jako publiczny URL HTML i przepisać deklarację do Play Console. |
| P1 | Uprawnienia wrażliwe | ⚠️ Częściowo ograniczone | Usunięto martwe `GET_ACCOUNTS` oraz `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`; harmonogram używa `setAlarmClock`. Nadal zostają `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `INTERNET` i `ACCESS_NETWORK_STATE`, które muszą być spójnie opisane w Play Console/listingu. |
| P1 | Release hardening | ✅ Domknięte w konfiguracji | Release ma włączone R8 minification i shrink resources. Do finalnego potwierdzenia zostaje build podpisanego AAB na właściwym upload keystore. |
| P1 | Numeracja wersji | ✅ Domknięte | `versionName` zmieniono z wartości snapshotowej na `0.7.0`. |
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

### 3. Health/Medical policy i właściwe pozycjonowanie produktu

Opis projektu mówi wprost, że aplikacja **nie ma doradzać medycznie ani samodzielnie ustalać dawkowania**. Ma pomagać w ewidencji leków, kontrolowaniu stanów magazynowych, terminów ważności i kończących się zapasów na podstawie dawkowania wpisanego przez użytkownika. Dlatego właściwe pozycjonowanie produktu to: **menedżer domowych zasobów produktów, które w tym przypadku są produktami leczniczymi**. To rozróżnienie jest ważne: w materiałach sklepowych i UI nie należy sugerować diagnozowania, leczenia, optymalizacji terapii, rekomendowania dawek ani oceny bezpieczeństwa medycznego.

Jednocześnie szczera ocena jest taka: samo nazwanie aplikacji „menedżerem zasobów” nie wystarczy, żeby wyjść poza zainteresowanie Google Play politykami zdrowotnymi. Aplikacja nadal operuje na produktach leczniczych, dawkowaniu wpisanym przez użytkownika, terminach ważności, stanach magazynowych i alertach kończących się zapasów. W praktyce należy przygotować Health apps declaration oraz jasny disclaimer, że aplikacja nie jest wyrobem medycznym, nie diagnozuje, nie leczy, nie zapobiega chorobom i nie ustala dawkowania; użytkownik odpowiada za poprawność wpisanych danych, a decyzje medyczne powinny być konsultowane z lekarzem lub farmaceutą.

Po poprawce repozytorium zawiera `docs/privacy_policy.md`, `docs/google_play_data_safety.md` oraz widoczny w aplikacji disclosure w ustawieniach. Ocena dla repozytorium: **domknięte**, ale Play Console nadal wymaga ręcznego wypełnienia Health apps declaration i opublikowania polityki prywatności pod publicznym URL.

Stan po poprawce i rekomendacje pozostające przed review:

1. Disclosure w aplikacji i disclaimer są dodane w Ustawieniach; teksty są dostępne w EN/PL/DE.
2. Dokument polityki prywatności jest dodany jako `docs/privacy_policy.md`; przed wysyłką trzeba go opublikować jako publiczny URL HTML i podać w Play Console.
3. Tę samą linię pozycjonowania umieścić w opisie Google Play: menedżer domowych zasobów produktów leczniczych, bez doradztwa medycznego i bez ustalania dawkowania.
4. W Play Console zadeklarować kategorię zdrowotną zgodną z faktycznymi funkcjami. Najbliżej jest `Medication and Treatment Management`, ale opis trzeba formułować ostrożnie jako inventory/stock/expiry-alert manager oparty na danych użytkownika, nie jako narzędzie prowadzenia terapii.
5. Unikać marketingowych claimów typu „poprawia leczenie”, „zapobiega pominięciu dawki”, „bezpieczne dawkowanie”, „optymalizuje terapię” albo „pilnuje poprawności dawek”, jeśli nie ma podstaw regulacyjnych/klinicznych.

### 4. Data safety, dane użytkownika i polityka prywatności

Aplikacja tworzy snapshot backupu zawierający email konta, listę produktów leczniczych, moc, substancję czynną, stan magazynowy, dawkowanie wpisane przez użytkownika, progi alertów, terminy/godziny alertów, etykiety i powiązania produktów z reminderami. Snapshot jest zapisywany lokalnie i wysyłany do Google Drive `appDataFolder` po autoryzacji użytkownika.

To oznacza co najmniej deklaracje Data safety dla:

- Health info / medication-related user data, nawet jeśli funkcja jest pozycjonowana jako domowa ewidencja zasobów, bo dane dotyczą produktów leczniczych i dawkowania wpisanego przez użytkownika.
- Personal info / email address lub account identifiers.
- App activity/settings, jeśli ujmowane są preferencje.
- Data transmitted off-device do Google Drive po akcji/zgodzie użytkownika.

Ocena po poprawce: **domknięte repozytoryjnie**. Dane, cele przetwarzania, Google Drive backup, zakres kontroli użytkownika i brak reklam/analityki są opisane w `docs/privacy_policy.md`, `docs/google_play_data_safety.md`, Ustawieniach aplikacji oraz opisie kopii na ekranie Konta. Pozostaje obowiązek publikacji polityki jako publiczny URL HTML i przepisania deklaracji do Play Console.

Rekomendacje pozostające przed review:

1. Opublikować `docs/privacy_policy.md` jako aktywny publiczny URL HTML, nie PDF, bez geofencingu.
2. W Play Console przepisać deklarację z `docs/google_play_data_safety.md`; nie deklarować „no data collected”.
3. Przed każdym releasem ponownie sprawdzić SDK/dependencies i dopisać nowe kategorie danych, jeśli pojawią się reklamy, analityka, crash reporting, płatności, lokalizacja, sensory albo nowe funkcje zdrowotne.
4. Rozważyć szyfrowanie snapshotu przed wysłaniem do Drive lub przynajmniej utrzymać jasną komunikację, że backup jest przechowywany w prywatnej przestrzeni aplikacji na Google Drive i chroniony przez konto Google.

### 5. Uprawnienia i wrażliwe API

Manifest po poprawce deklaruje: internet, stan sieci, powiadomienia, odbiór boot completed, full-screen intent i wibracje.

Stan po poprawce:

- Usunięto martwe `GET_ACCOUNTS`, bo `minSdk = 31` czyniło `maxSdkVersion=25` bezużytecznym.
- Usunięto `SCHEDULE_EXACT_ALARM` i `USE_EXACT_ALARM`, bo aktualny kod harmonogramu używa `AlarmManager.setAlarmClock`, a nie `setExact*`; to zmniejsza ryzyko deklaracji Restricted Permissions w Google Play.
- `USE_FULL_SCREEN_INTENT` nadal zostaje, bo aplikacja ma ekran dzwonienia dla alarmów produktów leczniczych. To nadal wymaga spójnego UX i opisu w Play Console/listingu.
- `POST_NOTIFICATIONS` i `RECEIVE_BOOT_COMPLETED` są funkcjonalnie uzasadnione alertami i odtwarzaniem harmonogramu po restarcie urządzenia.

Ocena: **ryzyko znacząco ograniczone, ale nie wyzerowane**.

Rekomendacje pozostające przed review:

1. W Play Console opisać `USE_FULL_SCREEN_INTENT` jako alarmowy ekran przypomnienia/alertu, nie jako funkcję marketingową lub komunikacyjną.
2. W opisie sklepu jawnie promować ewidencję, kontrolę zapasów, terminy ważności i alerty kończących się zapasów jako core feature, ale bez sugerowania, że aplikacja ustala dawkowanie lub prowadzi terapię.
3. Utrzymać fallback w aplikacji, gdy użytkownik nie udzieli zgód na powiadomienia/full-screen intent.

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

`backup_rules.xml` i `data_extraction_rules.xml` obejmują plik `drive_backup/medstock_medications_backup.json`. To oznacza, że backup aplikacji może przenosić właśnie snapshot danych produktów leczniczych. Jest to spójne funkcjonalnie, ale wymaga przejrzystego disclosure w privacy policy i Data safety. Użytkownik musi rozumieć, że w backupie znajdują się dane o lekach.

Ocena: **wymaga disclosure**.

### 9. Jakość release i hardening

Release ma teraz włączone `isMinifyEnabled = true` i `isShrinkResources = true`. To poprawia rozmiar oraz podstawowe utwardzenie artefaktu produkcyjnego.

Ocena: **domknięte w konfiguracji**.

Rekomendacje pozostające przed produkcją:

1. Zbudować podpisany release AAB na właściwym upload keystore.
2. Przetestować import rejestrów, skanowanie kodów, Google Drive backup/restore i alarmy po minifikacji.
3. Jeśli R8 ujawni problemy bibliotek Apache POI lub Google Auth, dopisać minimalne reguły keep zamiast wyłączać minifikację globalnie.

## Walidacja wykonana lokalnie

| Komenda | Wynik | Uwagi |
|---|---:|---|
| `chmod +x gradlew` | ✅ | Gradle wrapper jest wykonywalny. |
| `git fetch --depth=1000000 --all --tags --prune` | ✅ | Zgodnie z instrukcją użytkownika. |
| Bootstrap Android SDK z AGENTS.md | ✅ | Zainstalowano/zweryfikowano platform-tools, Android 36.1 i build-tools. |
| `./gradlew --no-daemon :app:assembleDebug` | ✅ | Build debug zakończony sukcesem. |
| `./gradlew --no-daemon :app:lintDebug` | ✅ | Lint debug zakończony sukcesem. |
| `./gradlew test --console=plain --no-daemon` | ✅ | Testy jednostkowe zakończone sukcesem. |
| `./gradlew --no-daemon :app:bundleRelease --console=plain` z tymczasowym lokalnym upload keystore | ✅ | Release AAB z R8 minify/shrink przechodzi technicznie; tymczasowy keystore nie jest commitowany. |
| `./gradlew --no-daemon :app:bundleRelease --console=plain` bez `keystore.properties` | ⚠️ | Build kończy się kontrolowanym, czytelnym błędem: trzeba dostarczyć `keystore.properties` na podstawie `keystore.properties.example`. |
| `zipinfo -1 app/build/outputs/apk/debug/app-debug.apk | rg '\.so$|^lib/' || true` | ✅ | Brak native libs w APK debug. |

## Minimalny plan naprawczy przed wrzuceniem do Google Play

1. **Napraw release signing i AAB**
   - Wykonane w repo: dodano `keystore.properties.example` i czytelny fail-fast dla release bez `keystore.properties`.
   - Po Twojej stronie: przygotuj upload keystore poza repo i wygeneruj finalny AAB: `./gradlew --no-daemon :app:bundleRelease`.

2. **Opublikuj compliance health/privacy poza repozytorium**
   - Repozytorium zawiera już politykę prywatności, Data safety draft, in-app disclosure i medyczny disclaimer.
   - Nadal trzeba opublikować politykę prywatności jako publiczny URL HTML i dodać ten URL w Play Console.

3. **Przygotuj deklaracje Play Console**
   - Health apps declaration: najbliższa kategoria `Medication and Treatment Management`, opisana jako ewidencja/stock/expiry-alert manager oparty na danych użytkownika.
   - Data safety: przepisać z `docs/google_play_data_safety.md` — medication-related health info, dawkowanie wpisane przez użytkownika, personal info/email, backup/cloud transfer, optionality.
   - Permissions declarations: exact alarm/full-screen intent, jeśli zostają.

4. **Uprość manifest**
   - Wykonane: usunięto martwe `GET_ACCOUNTS` oraz `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`.
   - Pozostaje: opisać `USE_FULL_SCREEN_INTENT` i powiadomienia w Play Console/listingu.

5. **Utwardź release**
   - Wykonane: włączono minify i shrink resources.
   - Pozostaje: przetestować krytyczne ścieżki po R8 na podpisanym AAB.

6. **Zmień wersję na produkcyjną**
   - Wykonane: `versionName` ustawiono na `0.7.0` bez sufiksu snapshot.
   - Pozostaje: przed każdą publikacją zwiększać `versionCode`.

## Szczera konkluzja

Założenie „wszystkie funkcje są wdrożone, więc aplikacja jest gotowa do sklepu” jest fałszywe. Funkcjonalna kompletność to tylko jeden wymiar. Masz rację, że produkt należy pozycjonować jako menedżer domowych zasobów produktów leczniczych, a nie aplikację medycznego doradztwa. Ale nie wolno wyciągać z tego zbyt wygodnego wniosku, że Google Play potraktuje dane o lekach i dawkowaniu jak zwykłą listę zakupów. Dla review równie ważne są: podpisywanie, deklaracje danych, polityka zdrowotna, zgodność uprawnień z listingiem, finalny AAB i proces testów. Na dziś projekt jest dobry jako build debug i kandydat do dalszego przygotowania, ale nie jako paczka gotowa do produkcyjnego review.
