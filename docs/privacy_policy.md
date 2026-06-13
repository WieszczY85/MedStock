# Polityka Prywatności aplikacji MedStock

**Data wejścia w życie:** 13 czerwca 2026 r.
**Ostatnia aktualizacja:** 13 czerwca 2026 r.
**Wersja dokumentu:** 1.0

Niniejsza Polityka Prywatności opisuje, w jaki sposób aplikacja mobilna **MedStock** („Aplikacja”) uzyskuje dostęp do danych, wykorzystuje je, przechowuje, przekazuje i umożliwia ich usunięcie. Dokument dotyczy wersji Aplikacji o identyfikatorze pakietu `pl.syntaxdevteam.medstock`.

## 1. Administrator i kontakt

Wydawcą Aplikacji i podmiotem odpowiedzialnym za jej działanie jest:

**WieszczY (SyntaxDevTeam)**
Repozytorium i kanał kontaktowy: <https://github.com/SyntaxDevTeam/MedStock_One>
Kontakt w sprawach prywatności: <https://github.com/SyntaxDevTeam/MedStock_One/issues>

W zgłoszeniu publicznym nie należy umieszczać informacji o przyjmowanych lekach, dawkowaniu, adresu e-mail, dokładnej lokalizacji ani innych danych wrażliwych. Jeżeli rozpatrzenie zgłoszenia wymaga takich danych, wydawca uzgodni bezpieczniejszy kanał komunikacji.

Wydawca nie wyznaczył inspektora ochrony danych.

> **Ważne przed publikacją:** jeżeli „WieszczY (SyntaxDevTeam)” nie jest pełną prawną nazwą administratora, wydawca powinien uzupełnić tę sekcję o swoje imię i nazwisko albo pełną nazwę podmiotu oraz dedykowany, niepubliczny adres e-mail do spraw prywatności. Sam publiczny system zgłoszeń GitHub nie jest właściwym miejscem do przesyłania danych dotyczących zdrowia.

## 2. Zakres i przeznaczenie Aplikacji

MedStock jest narzędziem do organizacji domowej apteczki. Umożliwia między innymi:

- prowadzenie własnej listy produktów leczniczych;
- zapisywanie stanu zapasów i dawkowania wprowadzonego przez użytkownika;
- tworzenie przypomnień oraz historii ich obsługi;
- wyświetlanie alertów o niskim lub pustym stanie zapasu;
- przeglądanie lokalnego katalogu produktów leczniczych i aptek opartego na publicznych rejestrach;
- skanowanie kodów znajdujących się na opakowaniach;
- opcjonalne ustalenie miasta na podstawie lokalizacji urządzenia w celu filtrowania katalogu aptek;
- opcjonalne połączenie konta Google oraz tworzenie kopii zapasowej w Google Drive;
- ręczny eksport lokalnej bazy danych.

Aplikacja **nie jest wyrobem medycznym**, nie diagnozuje, nie leczy, nie zapobiega chorobom, nie sprawdza poprawności dawkowania i nie zastępuje lekarza ani farmaceuty. Wyniki obliczeń, alerty i przypomnienia mają charakter organizacyjny i zależą od danych wpisanych przez użytkownika.

## 3. Najważniejsza informacja o przepływie danych

Większość danych użytkownika jest przetwarzana wyłącznie lokalnie, w prywatnej przestrzeni Aplikacji na urządzeniu. Wydawca nie utrzymuje własnego serwera kont użytkowników i nie otrzymuje automatycznie kopii lokalnej bazy MedStock.

Dane opuszczają urządzenie tylko w związku z funkcjami sieciowymi opisanymi w tej Polityce, w szczególności:

1. pobieraniem publicznych katalogów;
2. opcjonalnym użyciem usług konta Google i Google Drive;
3. użyciem komponentu Google Code Scanner dostarczanego przez Usługi Google Play;
4. opcjonalnym geokodowaniem lokalizacji przez usługę dostępną w systemie Android;
5. otwarciem wybranej lokalizacji apteki w zewnętrznej aplikacji mapowej lub przeglądarce;
6. ręcznym eksportem pliku do miejsca wskazanego przez użytkownika;
7. systemową kopią Android lub przenoszeniem danych na nowe urządzenie, jeżeli funkcja jest dostępna i włączona na urządzeniu.

## 4. Kategorie przetwarzanych danych

### 4.1. Dane o produktach leczniczych i zapasach

W zależności od sposobu korzystania z Aplikacji mogą to być:

- nazwa produktu;
- moc i substancja czynna;
- postać, droga podania, dane opakowania, jednostka oraz kod opakowania;
- aktualny stan zapasu;
- dawkowanie wpisane przez użytkownika;
- próg alertu i orientacyjna liczba dni pozostałego zapasu;
- identyfikatory rekordów i powiązania pomiędzy lekami a przypomnieniami.

Dane te mogą pośrednio lub bezpośrednio ujawniać informacje o zdrowiu użytkownika albo innej osoby, której apteczką zarządza użytkownik. Należy traktować je jako dane szczególnie wrażliwe.

### 4.2. Przypomnienia i historia działań

Aplikacja może przechowywać:

- godzinę przypomnienia;
- wybrane dni tygodnia;
- status włączenia;
- własną etykietę przypomnienia;
- wybrany dźwięk;
- leki powiązane z przypomnieniem;
- informacje o oznaczeniu dawki jako przyjętej, pominiętej albo odłożonej oraz czas takiego działania.

### 4.3. Dane konta Google

Po dobrowolnym połączeniu konta Google Aplikacja może przetwarzać:

- wybrany adres e-mail konta Google;
- adres URL zdjęcia profilowego i samo zdjęcie wyświetlane w Aplikacji;
- tokeny autoryzacyjne obsługiwane przez mechanizmy Google na urządzeniu;
- datę ostatniej wykonanej kopii zapasowej;
- stan włączenia kopii zapasowej.

Aplikacja żąda wyłącznie zakresów potrzebnych do pobrania podstawowego zdjęcia profilu oraz dostępu do prywatnego obszaru danych Aplikacji w Google Drive (`drive.appdata`). Nie uzyskuje ogólnego dostępu do wszystkich plików użytkownika w Google Drive.

### 4.4. Lokalizacja

Po wybraniu funkcji wykrywania miasta i udzieleniu systemowej zgody Aplikacja może jednorazowo uzyskać przybliżoną lub dokładną bieżącą lokalizację urządzenia. Współrzędne są używane do ustalenia nazwy miasta i filtrowania katalogu aptek.

Aplikacja:

- nie śledzi lokalizacji w tle;
- nie tworzy historii lokalizacji;
- nie zapisuje współrzędnych w swojej bazie;
- nie umieszcza lokalizacji w kopii Google Drive;
- nie wykorzystuje lokalizacji do reklam ani profilowania.

Ustalenie miasta wykorzystuje systemową usługę geokodowania Android. Zależnie od producenta urządzenia i skonfigurowanego dostawcy usługi współrzędne mogą zostać przekazane temu dostawcy. Zasady takiego przetwarzania określa dostawca systemowej usługi geokodowania.

### 4.5. Kody opakowań

Skanowanie jest realizowane przez Google Code Scanner w ramach Usług Google Play. MedStock otrzymuje wynik skanowania, czyli tekst lub numer kodu, i może użyć go do wyszukania produktu albo zapisania powiązania z produktem. MedStock nie otrzymuje i nie zapisuje obrazu z kamery.

Według dokumentacji Google przetwarzanie obrazu przez Google Code Scanner odbywa się na urządzeniu, a Google nie przechowuje obrazu ani wyniku skanowania. MedStock nie żąda bezpośrednio uprawnienia do aparatu dla tej funkcji.

### 4.6. Preferencje i dane techniczne Aplikacji

Lokalnie mogą być przechowywane między innymi:

- język, motyw i paleta kolorów;
- ustawienia widoku katalogów;
- stan trybu deweloperskiego;
- informacje o terminie ostatniej aktualizacji publicznych katalogów;
- stan wyświetlenia lub odrzucenia alertów;
- ustawienia powiadomień i harmonogramy zadań utrzymywane przez Android;
- lokalne metadane pobranych rejestrów, takie jak data aktualizacji, nagłówki `ETag` i `Last-Modified`.

### 4.7. Publiczne dane katalogowe

Aplikacja pobiera publicznie dostępne dane dotyczące produktów leczniczych, decyzji oraz aptek. Nie są to dane przekazane przez użytkownika. Mogą one zawierać informacje zawodowe lub rejestrowe opublikowane przez właściwe organy, w tym nazwy podmiotów odpowiedzialnych i dane placówek.

### 4.8. Dane, których Aplikacja celowo nie zbiera

Obecna wersja Aplikacji nie zawiera:

- reklam;
- SDK analitycznych;
- SDK do raportowania awarii;
- profilowania reklamowego;
- płatności;
- dostępu do kontaktów, wiadomości, mikrofonu lub kalendarza;
- własnego serwera telemetrycznego wydawcy.

Wydawca nie sprzedaje danych użytkowników ani nie udostępnia ich brokerom danych.

## 5. Cele i podstawy przetwarzania

Dane są przetwarzane w następujących celach:

| Cel | Dane | Charakter funkcji | Podstawa |
|---|---|---|---|
| Prowadzenie listy leków, zapasów, dawkowania, przypomnień i historii | dane opisane w pkt 4.1–4.2 | podstawowa funkcja Aplikacji, inicjowana przez użytkownika | wykonanie funkcjonalności żądanej przez użytkownika; dane pozostają zasadniczo lokalnie |
| Obliczanie stanów zapasu i generowanie alertów | dane o zapasie i dawkowanie | podstawowa funkcja Aplikacji | wykonanie funkcjonalności żądanej przez użytkownika |
| Wysyłanie powiadomień, alarmów i odtwarzanie harmonogramu po restarcie | harmonogram, nazwy lub etykiety widoczne w powiadomieniu | opcjonalne, zależne od ustawień i zgód Android | zgoda użytkownika udzielona w ustawieniach systemowych oraz żądanie wykonania funkcji |
| Filtrowanie aptek według bieżącego miasta | jednorazowa lokalizacja i ustalona nazwa miasta | opcjonalne | zgoda na lokalizację i działanie podjęte przez użytkownika |
| Połączenie konta i wyświetlenie profilu | adres e-mail, URL i obraz awatara | opcjonalne | autoryzacja Google i działanie podjęte przez użytkownika |
| Kopia i odtworzenie danych w Google Drive | adres e-mail, leki, zapasy, dawkowanie, przypomnienia i preferencje | opcjonalne | włączenie funkcji i autoryzacja dostępu do Google Drive przez użytkownika |
| Eksport bazy danych | cała zawartość lokalnej bazy | opcjonalne | jednoznaczne polecenie użytkownika i wybrane przez niego miejsce zapisu |
| Aktualizacja katalogów | metadane połączenia i publiczne pliki rejestrowe | niezbędne do aktualności katalogu | zapewnienie funkcjonalności katalogu |
| Bezpieczeństwo i obsługa zgłoszeń | dane dobrowolnie przekazane wydawcy | tylko po nawiązaniu kontaktu | odpowiedź na zgłoszenie oraz uzasadniony interes polegający na ochronie i rozwijaniu Aplikacji |

W odniesieniu do danych mogących ujawniać stan zdrowia Aplikacja działa jako narzędzie pozostające pod kontrolą użytkownika. Użytkownik sam decyduje, jakie informacje wprowadza oraz czy wysyła ich kopię do Google Drive. Nie należy wpisywać danych innych osób bez odpowiedniej podstawy i ich wiedzy.

Jeżeli właściwe przepisy wymagają zgody na określone przetwarzanie, użytkownik może ją wycofać przez wyłączenie funkcji, cofnięcie uprawnienia Android, odłączenie konta albo cofnięcie dostępu Google. Wycofanie zgody nie wpływa na zgodność z prawem działań wykonanych przed jej wycofaniem.

## 6. Przechowywanie danych na urządzeniu

Dane użytkownika, katalogi i preferencje są zapisywane w prywatnej przestrzeni Aplikacji, między innymi w lokalnej bazie SQLite, plikach Aplikacji i `SharedPreferences`. Inne aplikacje co do zasady nie mają dostępu do tej przestrzeni bez uprawnień systemowych, podatności urządzenia albo ingerencji użytkownika.

Dane lokalne są przechowywane do chwili:

- ich usunięcia lub zmiany w Aplikacji;
- wyczyszczenia danych Aplikacji w ustawieniach Android;
- odinstalowania Aplikacji, z zastrzeżeniem systemowych kopii zapasowych;
- zastąpienia danych podczas odtwarzania kopii;
- usunięcia przez system zgodnie z zasadami Android.

Historia obsługi przypomnień jest przechowywana lokalnie do chwili usunięcia powiązanych danych, wyczyszczenia danych Aplikacji lub jej odinstalowania.

## 7. Kopia zapasowa Google Drive

### 7.1. Dobrowolność

Połączenie konta Google i kopia w Google Drive są opcjonalne. Podstawowe lokalne funkcje MedStock są dostępne bez łączenia konta.

### 7.2. Zawartość kopii

Kopia ma postać pliku JSON i może zawierać:

- adres e-mail wybranego konta, używany również do sprawdzenia zgodności konta podczas odtwarzania;
- datę utworzenia;
- listę leków, ich moc, substancję czynną, opakowanie, jednostkę, zapas, dawkowanie i progi alertów;
- przypomnienia, godziny, dni, etykiety, dźwięki i powiązania z lekami;
- ustawienia języka, motywu i palety kolorów.

Obecny format kopii Google Drive nie obejmuje historii potwierdzeń, pominięć i odłożeń dawek.

### 7.3. Sposób działania i bezpieczeństwo

Kopia jest przesyłana przez szyfrowane połączenie HTTPS do prywatnego obszaru `appDataFolder` na koncie Google użytkownika. Plik nie jest widoczny jak zwykły dokument na Dysku, lecz jest dostępny dla MedStock po udzieleniu właściwej autoryzacji.

MedStock nie stosuje dodatkowego szyfrowania end-to-end ani hasła użytkownika do pliku przed wysłaniem. Ochrona kopii zależy zatem również od zabezpieczeń urządzenia, konta Google i infrastruktury Google.

Po włączeniu kopia może być tworzona niezwłocznie, ręcznie oraz automatycznie w tle, zwykle w przybliżeniu raz na 24 godziny, gdy dostępna jest sieć. Android może opóźnić wykonanie zadania. Kolejna kopia aktualizuje istniejący plik MedStock znaleziony w `appDataFolder`.

### 7.4. Wyłączenie i usunięcie

Wyłączenie kopii lub odłączenie konta zatrzymuje kolejne kopie i usuwa lokalne dane połączenia konta, ale **nie gwarantuje automatycznego usunięcia wcześniej przesłanego pliku z Google Drive**.

Aby usunąć zdalne dane, użytkownik powinien skorzystać z ustawień konta Google lub Google Drive, w szczególności usunąć ukryte dane Aplikacji, albo cofnąć MedStock dostęp do konta. Szczegółowe opcje zależą od aktualnych ustawień usług Google.

## 8. Systemowa kopia Android i przenoszenie danych

Aplikacja zezwala Androidowi na wykonywanie kopii zapasowej, ale reguły Aplikacji ograniczają objęte nią pliki do lokalnego pliku snapshotu `drive_backup/medstock_medications_backup.json`, jeżeli taki plik wcześniej utworzono. Ten sam plik może uczestniczyć w systemowym przenoszeniu danych na nowe urządzenie.

Oznacza to, że kopia zawierająca dane opisane w pkt 7.2 może zostać przekazana do usługi kopii zapasowych skonfigurowanej na urządzeniu, nawet niezależnie od bezpośredniego przesłania jej przez funkcję Google Drive w MedStock. Dostawcą systemowej kopii jest zazwyczaj dostawca systemu lub konta skonfigurowanego na urządzeniu. Dostępność, retencję, szyfrowanie i usuwanie takiej kopii określają ustawienia Android oraz polityka tego dostawcy.

Użytkownik może zarządzać systemową kopią w ustawieniach Android. Wyczyszczenie danych lub odinstalowanie Aplikacji nie musi natychmiast usuwać kopii przechowywanej przez dostawcę systemowej usługi backupu.

## 9. Ręczny eksport bazy danych

Użytkownik może wyeksportować lokalną bazę do pliku w wybranym przez siebie miejscu za pomocą systemowego selektora dokumentów. Eksport może zawierać dane o lekach, dawkowaniu, zapasach, przypomnieniach, historii działań oraz lokalne katalogi publiczne.

Po zapisaniu poza prywatną przestrzenią Aplikacji:

- MedStock nie kontroluje dalszego przechowywania ani udostępniania pliku;
- plik może być dostępny dla wybranego dostawcy pamięci, usługi chmurowej lub innych osób mających dostęp do urządzenia;
- użytkownik odpowiada za jego zabezpieczenie i usunięcie.

Wyeksportowana baza nie jest dodatkowo szyfrowana przez MedStock. Nie należy wysyłać jej w publicznym zgłoszeniu błędu.

## 10. Odbiorcy i zewnętrzni dostawcy

W zależności od użytych funkcji dane mogą być przetwarzane przez:

### 10.1. Google

- **Usługi Google Play / Google Code Scanner** – realizacja skanowania kodu;
- **Google OAuth i usługa informacji o profilu** – autoryzacja i pobranie zdjęcia profilowego;
- **Google Drive API** – zapis i odczyt opcjonalnej kopii w `appDataFolder`;
- **Google Maps lub przeglądarka** – tylko gdy użytkownik wybierze otwarcie adresu apteki; do zewnętrznej aplikacji przekazywane jest zapytanie tekstowe obejmujące nazwę i adres apteki;
- **usługi systemowe Android** – zależnie od urządzenia mogą obsługiwać geokodowanie, backup, powiadomienia i harmonogramy.

Google przetwarza dane zgodnie z własnymi warunkami i polityką prywatności: <https://policies.google.com/privacy>.

### 10.2. Publiczne rejestry

Aplikacja łączy się z publicznymi serwisami e-zdrowie w celu pobrania katalogów:

- <https://rdg.ezdrowie.gov.pl/Decision/DownloadPublicXml>
- <https://rejestry.ezdrowie.gov.pl/api/rpl/medicinal-products/public-pl-report/get-xlsx>
- <https://rejestry.ezdrowie.gov.pl/api/rpl/medicinal-products/public-pl-report/get-csv>
- <https://rejestry.ezdrowie.gov.pl/api/ra/filegenerator/getxls>
- <https://rejestry.ezdrowie.gov.pl/api/ra/filegenerator/getcsv>

Do tych serwisów nie jest celowo wysyłana lista leków użytkownika, dawkowanie ani lokalna baza. Jak przy każdym połączeniu internetowym administrator serwisu może otrzymać standardowe dane transmisyjne, takie jak adres IP, czas połączenia, nagłówki HTTP i informacje techniczne konieczne do obsługi żądania.

### 10.3. Dostawcy wybrani przez użytkownika

Jeżeli użytkownik zapisze eksport w zewnętrznej chmurze, otworzy mapę albo korzysta z systemowej kopii, odpowiedni dostawca otrzyma dane potrzebne do wykonania tej operacji na zasadach swojej polityki prywatności.

Wydawca nie udostępnia danych użytkownika reklamodawcom i nie używa ich do marketingu behawioralnego.

## 11. Przekazywanie danych poza Europejski Obszar Gospodarczy

Korzystanie z usług Google lub innego wybranego dostawcy może wiązać się z przetwarzaniem danych w państwach poza Europejskim Obszarem Gospodarczym, w tym w Stanach Zjednoczonych. Podstawy i zabezpieczenia takich transferów, na przykład decyzje stwierdzające odpowiedni stopień ochrony, standardowe klauzule umowne lub inne mechanizmy prawne, określa właściwy dostawca w swojej dokumentacji.

Wydawca nie przesyła lokalnej bazy do własnej infrastruktury poza EOG.

## 12. Uprawnienia Android

Aplikacja może korzystać z następujących uprawnień:

- **Internet i stan sieci** – pobieranie katalogów, usługi Google i kopia Drive;
- **lokalizacja przybliżona i dokładna** – ustalenie miasta na wyraźne żądanie użytkownika;
- **powiadomienia** – alerty o zapasach i przypomnienia;
- **dokładne alarmy** – uruchomienie przypomnienia o wybranej godzinie;
- **pełnoekranowe alarmy** – pokazanie ekranu przypomnienia, także przy zablokowanym ekranie, jeśli Android na to zezwala;
- **uruchomienie po restarcie** – ponowne zaplanowanie przypomnień;
- **wibracje** – sygnalizowanie alertu lub przypomnienia.

Odmowa uprawnienia ogranicza powiązaną funkcję, ale nie powinna uniemożliwiać korzystania z niezależnych funkcji Aplikacji. Uprawnieniami można zarządzać w ustawieniach Android.

Treść powiadomień może być widoczna na ekranie blokady zgodnie z ustawieniami urządzenia. Użytkownik powinien dostosować ustawienia prywatności powiadomień, jeżeli nazwy leków lub etykiety przypomnień nie powinny być widoczne dla innych osób.

## 13. Retencja i usuwanie danych

| Miejsce | Okres przechowywania | Sposób usunięcia |
|---|---|---|
| Prywatna przestrzeń MedStock | do usunięcia danych, wyczyszczenia danych Aplikacji lub odinstalowania | usunięcie wpisów w Aplikacji, „Wyczyść dane” w Android albo odinstalowanie |
| Lokalny snapshot JSON | do zastąpienia/usunięcia pliku, wyczyszczenia danych lub odinstalowania, z zastrzeżeniem backupu Android | wyczyszczenie danych Aplikacji; usunięcie kopii systemowej osobno u jej dostawcy |
| Google Drive `appDataFolder` | do zastąpienia lub usunięcia przez użytkownika/dostawcę | usunięcie ukrytych danych Aplikacji albo zarządzanie dostępem i danymi na koncie Google |
| Eksport bazy | do usunięcia przez użytkownika lub wybranego dostawcę pamięci | usunięcie pliku w miejscu docelowym i ewentualnych jego kopii |
| Dane przekazane w zgłoszeniu | tak długo, jak jest to potrzebne do obsługi zgłoszenia, bezpieczeństwa projektu i zachowania uzasadnionej dokumentacji | kontakt z wydawcą; publiczne zgłoszenie można samodzielnie edytować tylko w zakresie zapewnianym przez GitHub |
| Logi i dane transmisyjne zewnętrznych usług | według polityki danego dostawcy | narzędzia i procedury usunięcia danego dostawcy |

Wydawca nie może usunąć danych, do których nie ma dostępu, na przykład lokalnych danych na urządzeniu, pliku w prywatnym Google Drive albo eksportu przechowywanego przez użytkownika. W takich przypadkach usunięcie wykonuje użytkownik lub właściwy dostawca usługi.

## 14. Prawa użytkownika

W zakresie, w jakim wydawca jest administratorem danych osobowych i zastosowanie ma RODO, użytkownik może mieć prawo do:

- uzyskania informacji o przetwarzaniu;
- dostępu do danych i otrzymania ich kopii;
- sprostowania danych;
- usunięcia danych;
- ograniczenia przetwarzania;
- przenoszenia danych;
- wniesienia sprzeciwu wobec przetwarzania opartego na uzasadnionym interesie;
- wycofania zgody w dowolnym momencie, jeżeli przetwarzanie opiera się na zgodzie;
- wniesienia skargi do właściwego organu nadzorczego.

W Polsce organem nadzorczym jest **Prezes Urzędu Ochrony Danych Osobowych**, <https://uodo.gov.pl/>.

Ponieważ wydawca nie ma zdalnego dostępu do danych przechowywanych wyłącznie na urządzeniu lub w prywatnym obszarze Google Drive, prawa dotyczące takich danych użytkownik realizuje przede wszystkim bezpośrednio w Aplikacji, ustawieniach Android i ustawieniach konta Google.

Żądanie dotyczące danych faktycznie otrzymanych przez wydawcę można przekazać kanałem wskazanym w pkt 1. Wydawca może poprosić o informacje konieczne do zweryfikowania żądania, ale nie należy przesyłać publicznie danych zdrowotnych.

## 15. Bezpieczeństwo

Stosowane środki obejmują:

- przechowywanie danych lokalnych w prywatnej przestrzeni Aplikacji;
- szyfrowanie transmisji do wskazanych usług przez HTTPS;
- ograniczony zakres Google Drive `drive.appdata`;
- brak reklamowych i analitycznych SDK;
- brak bezpośredniego dostępu MedStock do obrazu z kamery podczas skanowania;
- systemowe mechanizmy uprawnień Android;
- kontrolę użytkownika nad eksportem, lokalizacją, powiadomieniami i połączeniem konta.

Żaden system nie gwarantuje całkowitego bezpieczeństwa. Istotne ryzyka obejmują utratę odblokowanego urządzenia, przejęcie konta Google, zapis niezaszyfrowanego eksportu w publicznym miejscu, wyświetlenie treści powiadomień na ekranie blokady oraz urządzenia z osłabionymi zabezpieczeniami systemowymi.

Użytkownik powinien chronić urządzenie blokadą ekranu, konto Google uwierzytelnianiem wieloskładnikowym oraz samodzielnie zabezpieczać wyeksportowane pliki.

## 16. Dzieci

Aplikacja nie jest kierowana do dzieci i nie jest przeznaczona do samodzielnego podejmowania przez nie decyzji dotyczących leków. Osoby niepełnoletnie powinny korzystać z Aplikacji pod nadzorem rodzica, opiekuna lub odpowiedzialnej osoby dorosłej, zgodnie z prawem właściwym dla ich miejsca zamieszkania.

Wydawca nie zbiera świadomie danych dzieci przez własny serwer. Jeżeli użytkownik przechowuje w MedStock dane dotyczące dziecka lub innej osoby, odpowiada za posiadanie odpowiedniej podstawy i zabezpieczenie tych danych.

## 17. Zautomatyzowane decyzje

MedStock wykonuje proste obliczenia na podstawie danych użytkownika, na przykład szacuje liczbę dni zapasu i kwalifikuje zapas jako wystarczający, niski lub pusty. Nie jest to profilowanie ani zautomatyzowane podejmowanie decyzji wywołujące skutki prawne lub podobnie istotnie wpływające na użytkownika.

Obliczenia mogą być błędne z powodu niepoprawnych, niepełnych lub nieaktualnych danych. Nie należy na ich podstawie samodzielnie zmieniać leczenia, dawkowania ani przerywać przyjmowania leku.

## 18. Zmiany Polityki

Polityka może zostać zmieniona, gdy zmienią się funkcje Aplikacji, dostawcy, przepisy lub sposób przetwarzania danych. Aktualna wersja powinna być publikowana pod stałym, publicznie dostępnym adresem i wskazywać datę ostatniej aktualizacji.

Jeżeli zmiana istotnie wpływa na sposób użycia danych, wydawca powinien poinformować o niej w Aplikacji lub w opisie aktualizacji przed rozpoczęciem nowego przetwarzania, jeżeli wymagają tego przepisy.

## 19. Informacje o publikacji dokumentu

Wymogi Google Play przewidują udostępnienie Polityki Prywatności:

1. pod publicznym, aktywnym adresem URL;
2. bez wymogu logowania i bez ograniczeń geograficznych;
3. w polu Polityki Prywatności w Google Play Console;
4. również jako link lub tekst dostępny z poziomu Aplikacji;
5. w treści zgodnej z deklaracją „Bezpieczeństwo danych” w Google Play.

Repozytoryjny plik Markdown może być źródłem dokumentu, ale przed publikacją należy udostępnić go jako czytelną stronę internetową i sprawdzić wszystkie dane administratora oraz kanały kontaktu.

## 20. Źródła i polityki zewnętrzne

- RODO – Rozporządzenie (UE) 2016/679: <https://eur-lex.europa.eu/eli/reg/2016/679/oj>
- Zasady Google Play dotyczące danych użytkownika: <https://support.google.com/googleplay/android-developer/answer/10144311>
- Polityka prywatności Google: <https://policies.google.com/privacy>
- Dokumentacja Google Code Scanner: <https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner>
- Urząd Ochrony Danych Osobowych: <https://uodo.gov.pl/>

---

**Kontakt:** WieszczY (SyntaxDevTeam), <https://github.com/SyntaxDevTeam/MedStock_One/issues>
**Prośba o bezpieczeństwo:** nie publikuj w zgłoszeniu danych o zdrowiu, adresu e-mail, dokładnej lokalizacji ani pliku wyeksportowanej bazy MedStock.
