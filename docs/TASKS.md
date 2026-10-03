# Guardian — pozostałe taski dla dwóch osób

Aktualizacja: 3 października 2026. Lista zawiera tylko pracę pozostałą do wykonania lub odbioru. Ukończone elementy opisują [architektura](ARCHITECTURE.md) i historia Git. Priorytet na teraz: aplikacja uruchomiona jako własny build na podłączonym Androidzie. Dokończenie Gemmy i benchmark są odłożone zgodnie z decyzją zespołu.

## Podział odpowiedzialności

| Osoba | Obszar | Główne katalogi | Co można robić równocześnie |
| --- | --- | --- | --- |
| Osoba 1 | Android, model i jakość analizy | `modules/guardian`, `benchmarks`, `plugins`, `scripts` | Testy silnika i listenera bez zmieniania UI |
| Osoba 2 | Expo, UX i demo | `src/app`, `src/components`, `src/features`, `docs` | Ekrany i stany ochrony na podstawie istniejącego kontraktu, bez czekania na model |

Wspólny kontrakt znajduje się w `src/types/guardian.ts`, a adapter w `src/services/guardian.ts`. Zmiany nazw i typów metod najpierw ustalcie razem. Nie zmieniajcie równocześnie `app.json`, `package.json` i lockfile; instalacje dependencies koordynuje jedna osoba. Surowe wiadomości nie mogą przekraczać granicy Kotlin → JS.

## Osoba 1 — Android i AI

### P0 odłożone do czasu powrotu do Gemmy

- [ ] A01 Dokończyć i zweryfikować Gemma 3 1B na fizycznym telefonie. Kod adaptera LiteRT-LM jest przygotowany, ale nie ma odebranego modelu ani pomiaru. Uzyskać licencjonowany `.litertlm`, potwierdzić SHA-256, wersję artefaktu, działanie GPU/CPU, czas startu i RAM. Odbiór: rzeczywista analiza offline po restarcie. Jeśli jakość za niska, zbadać inny mały model. Dawne R02/N04.
- [ ] A02 Uruchomić benchmark istniejących 40 przypadków testowych. Raport ma zawierać precision, recall, false positives, uncertain, JSON validity, p50/p95 i próbki PSS; instrukcja w `benchmarks/README.md`. Nie dostrajać promptu na tym zbiorze. Odbiór: rzeczywisty raport z modelu i telefonu, nie symulacja. Zależność: A01. Dawne R03.

### P0 odbiór pipeline na fizycznym Androidzie

- [ ] A03 Sprawdzić prawdziwe powiadomienia SMS/WhatsApp/Messenger po świadomym włączeniu dostępu przez użytkownika. Użyć testowych rozmów bez prywatnych danych. Zidentyfikować stabilne klucze konwersacji, aktualizacje MessagingStyle, fallback i summary. Odbiór: brak duplikatów i mieszania rozmów; lista ograniczeń konkretnych aplikacji. Dawne R04/N02.
- [ ] A04 Odebrać lifecycle: dostęp denied/granted/revoked, rebind, restart procesu, pauza, cancel, usunięcie powiadomienia i kasowanie historii w czasie inference. Sprawdzić, że późny wynik nie wraca po usunięciu. Odbiór: krótki protokół scenariuszy na telefonie. Zależności: A01, A03 dla pełnego flow. Dawne N01/N03/N06/N07.
- [ ] A05 Odebrać systemowe ostrzeżenia: POST_NOTIFICATIONS denied/granted, kanał, rate limit, cold-start deep link, foreground/background. Odbiór: rzeczywiste high daje jedno ostrzeżenie i otwiera odpowiedni wynik; blokada powiadomień nie niszczy historii. Zależności: A01 i A03. Dawne N08.

### P1 po odbiorze P0

- [ ] A06 Model manager: katalog sprawdzonych artefaktów, rozmiar i wolne miejsce, checksum wobec znanego katalogu, pobieranie z postępem, cancel, retry, uszkodzony plik. Import lokalnego pliku jest punktem startu, nie pełnym managerem. Dawne U02.
- [ ] A07 Wybór aplikacji do monitorowania, test zużycia baterii, zachowanie po długiej bezczynności i ograniczeniach producenta. Sprawdzić, czy potrzebna jest dodatkowa architektura pracy w tle. Dawne U04/R04.
- [ ] A08 Uzupełnić pomiar driver hang / OOM / timeout inicjalizacji. Obecny timeout dotyczy inference, nie gwarantuje przerwania natywnego `initialize`.

## Osoba 2 — Expo i UX

### P0 odbiór i dopracowanie aplikacji

- [ ] B01 Odebrać konfigurację na telefonie: powrót z ustawień systemowych, loader i błąd importu, brak modelu, brak dostępu, unavailable na iOS/web. Status „aktywna” musi zależeć od rzeczywistego listenera, gotowego modelu i monitorowania. UI nie może obiecywać ochrony przy brakującym komponencie. Można wykonać teraz bez Gemmy; pełny ready później z A01.
- [ ] B02 Odebrać nawigację: onboarding nie wraca po Android Back, zakładki zachowują miejsce, szczegóły mają bazową nawigację pod deep linkiem. Sprawdzić start zimny/ciepły i brak wyniku po retencji. Zakładki i flow są zaimplementowane; pozostaje pełny odbiór na urządzeniu.
- [ ] B03 Sprawdzić light/dark, duży tekst, TalkBack, kontrast i mały ekran. Nagrać pełny flow na fizycznym Androidzie; pomiar wydajności robić w release build. Nie zastępować pomiaru fps screenshotami. Dawne U06.

### P1 produkt i prezentacja

- [ ] B04 Rozdzielić empty/loading/error/paused/uncertain w finalnym UX historii i dashboardu; przejrzeć długie treści, etykiety i zalecenia. Aktualny UI jest bazą do iteracji. Dawne U03.
- [ ] B05 Dodać „błędne ostrzeżenie” i dobrowolny lokalny feedback. „Sprawdzone” już istnieje; feedback nie zmienia automatycznie ryzyka i nie wysyła rozmów. Dawne U05.
- [ ] B06 Przygotować syntetyczny scenariusz demonstracyjny z rzeczywistym modelem: podszywanie się pod rodzinę i poprawna rozmowa. Pokazać offline i kontrolę użytkownika. Osoba 1 dostarcza wejście/native; osoba 2 przygotowuje przebieg i recording. Zależności: A01–A05. Dawne D01/D02.
- [ ] B07 Przygotować prezentację PDF do 10 slajdów i opis zgłoszenia. Dane zespołu i granicę prac przed eventem dostarcza zespół. Ujawnić istotne AI i zasoby z `RESOURCES.md`. Wymagania są zweryfikowane w `CHALLENGE.md`. Dawne D03/R01.
- [ ] B08 Przygotować preview APK i końcowy smoke test przed prezentacją. Nie zastępować awarii lokalnego modelu ukrytym cloud fallbackiem. Zależność: A01–A05, B01–B03. Dawne D04.

## P2 po MVP

- [ ] A09 Enhanced AI Analysis: osobna zgoda, redakcja PII z pomiarem jakości, backend bez zapisu rozmów, limity i timeout. Wyłącznie po osobnej decyzji zespołu i przeglądzie prywatności.
- [ ] A10 Poszerzyć benchmark o inne telefony, języki i nowe manipulacje; nie przedstawiać 40 przykładów jako produkcyjnej skuteczności.
- [ ] B09 Zaprojektować alternatywne wejście na iOS; nie obiecywać dostępu do powiadomień innych aplikacji.
- [ ] B10 Ustalić dystrybucję, aktualizacje modeli i ewentualną monetyzację.

## Synchronizacja dwóch osób

1. Teraz: osoba 1 przygotowuje A03/A04 bez prywatnych danych; osoba 2 robi B01–B03. A01/A02 czekają na powrót do Gemmy.
2. Po powrocie do modelu: osoba 1 robi A01/A02; osoba 2 robi B04/B05/B07 na ustalonym kontrakcie.
3. Po gotowym pipeline: wspólnie odebrać A04/A05 i B06/B08. Zespół potrzebuje działającego lokalnego modelu do demonstracji ochrony.
