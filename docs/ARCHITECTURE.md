# Guardian — architektura P0

## Granica prywatności

`modules/guardian/android` jest lokalnym modułem Expo, autolinkowanym tylko na Androidzie. Manifest modułu deklaruje listener i uprawnienie do publikowania ostrzeżeń. Kod `android/` w katalogu repo jest generowany przez CNG i ignorowany; zmiany kompilatora przechodzą przez config plugin `plugins/withGuardianAndroid.js` i `expo-build-properties`.

Listener uruchamia singleton `GuardianRuntime` niezależnie od JS. Domyślnie monitorowanie jest wyłączone. Zgoda systemowa i świadome włączenie monitorowania są osobnymi warunkami. Wybrane źródła: WhatsApp, Messenger, Google Messages i Samsung Messages. Nie odczytujemy historii aplikacji ani baz komunikatorów.

Normalizacja korzysta z AndroidX MessagingStyle i fallbacku tekstu. Powiadomienia zbiorcze i własne są odrzucane. Konwersacja jest grupowana przez pakiet i shortcut; bez shortcut używa tożsamości powiadomienia, a nie nazwy kontaktu. Klucze i tożsamości wiadomości są haszowane i nie opuszczają Kotlin. Deduplikacja MessagingStyle używa timestampu i nadawcy; fallback używa klucza powiadomienia i hasza treści. Zmiana treści pod tym samym timestampem aktualizuje wiadomość.

Kontekst: 32 konwersacje, 5 wiadomości, 1500 znaków każda, monotoniczny TTL 15 minut. Okresowy sweep co 30 sekund usuwa wygasłe wpisy także bez nowych wiadomości. Wygasłe wiadomości są pomijane przed inference; fizyczne usunięcie z RAM może nastąpić do 30 sekund po TTL. Pauza, utrata połączenia lub zgody czyszczą bufor i kolejkę. Kolejka jest ograniczona do 16 zadań. Generacja lifecycle zapobiega zapisaniu wyniku rozpoczętego przed pauzą/usunięciem historii. Usunięcie powiadomienia blokuje zapis jego wcześniejszego zadania. W restartowanym procesie nie istnieje dawny kontekst RAM.

## Inference

LiteRT-LM jest przypięty do 0.15.0. Runtime używa CPU. GPU na testowym Galaxy S22 inicjalizował się, ale nie zwracał poprawnych wyników; nie jest włączony w tej konfiguracji. Gotowość wymaga poprawnej oceny syntetycznej, nieszkodliwej wiadomości. Nie gwarantujemy zgodności każdego artefaktu z każdym backendem. Gemma 3 1B IT INT4 jest przypięta do rewizji i SHA-256 w `models/catalog/gemma3-1b.json`. Picker kopiuje plik do `noBackupFilesDir`, sprawdza dokładny rozmiar i checksum przed atomową podmianą. Błędny import zachowuje poprzedni plik; błąd inicjalizacji odrzuca operację. Import wyłącza monitorowanie. Model jest weryfikowany i ładowany po starcie procesu. Skrypt `npm run model:download` pobiera artefakt dopiero po uzyskaniu dostępu przez użytkownika; licencję użytkownik akceptuje osobiście. Instrukcja: [GEMMA.md](GEMMA.md).

Engine działa poza wątkiem UI. Mutex serializuje init, inference i close. Adapter używa callback API LiteRT-LM zamiast gotowego wrappera Flow, który w wersji 0.15.0 odwołuje się do niezgodnej metody binarnej SendChannel w coroutine Expo 57. Każde zadanie tworzy osobną konwersację z krótką instrukcją i nieufnymi wiadomościami w JSON w turze user. Gemma 3 nie obsługuje osobnej roli system, więc prompt guardian-pl-v3-evidence umieszcza instrukcję przed danymi zgodnie z dokumentacją modelu. Domyślne parametry: topK=1, topP=0.9, temperature=1 (greedy przez topK=1), limit 256 output tokens. Timeout inference wynosi 30 sekund, a cancel wywołuje `cancelProcess`. Inicjalizacja JNI ma ograniczenia: nie ma twardego timeoutu samego `initialize`; to punkt do pomiaru na telefonie. Pauza czeka na bezpieczne zamknięcie engine. Obsługa OOM, zawieszenia drivera i trwałej pracy w tle wymaga dalszych testów.

Dekodowanie jest ograniczone przez JSON Schema z enumami z tego samego słownika Assessment. Model wybiera ryzyko, kategorię i maksymalnie dwa sygnały; przed alarmem wysoki wynik przechodzi drugą, krytyczną ocenę w osobnej konwersacji modelu; każdy uzasadnia krótkim cytatem z wejścia. Walidator sprawdza schemat i to, czy cytat występuje w wiadomościach, po czym usuwa cytaty. Do historii i Expo trafiają tylko enumy oraz analysisVersion, bez cytatów i nowych swobodnych pól. Nie ma słownika słów ani progów sygnałów zastępujących decyzję modelu; obecność cytatu nie dowodzi jego poprawnej interpretacji. Wyjaśnienia i zalecenia powstają w Kotlin z lokalnych szablonów. Malformed JSON, timeout i błąd inference nie zamieniają się w low; wynik jest uncertain. Model nie ma tools ani dostępu do sieci.

## Wyniki i ostrzeżenia

Historia: Atomically written plik JSON w `noBackupFilesDir`, schemaVersion=1, maks. 200 wyników przez 7 dni; nieobsługiwany schemat lub uszkodzony plik jest usuwany. Odczyt przycina retencję. Plik nie jest szyfrowany dodatkową warstwą aplikacji; korzysta z izolacji aplikacji i zabezpieczeń Androida. Usunięcie historii anuluje wcześniejsze zadania i systemowe ostrzeżenia. Zapis wyniku jest sprawdzany po powrocie inference względem aktualnej generacji, zgody i monitorowania.

Ostrzeżenie systemowe dotyczy high, ma prywatną widoczność na lock screen i prowadzi przez `guardian://alert/<uuid>` do szczegółów. Rate limit: ten sam wzorzec w konwersacji co 5 min i źródło co 1 min. Brak zgody na POST_NOTIFICATIONS nie blokuje historii, ale uniemożliwia systemowe ostrzeżenia. Część przypadków może być odrzucona przez OS lub ubijanie procesu; brak ostrzeżenia nie dowodzi bezpieczeństwa.

## Expo

Zod waliduje kontrakt statusu i wyników przy wejściu z mostu. TanStack Query obsługuje odczyty, native event i refresh po wznowieniu. Zustand zapisuje wyłącznie ukończenie onboardingu w AsyncStorage; historia i przełącznik monitorowania pozostają w Kotlin. Splash czeka na hydration. Onboarding jest chroniony `Stack.Protected`; po zakończeniu znika z historii nawigacji. Szczegóły mają zakładki jako anchor dla deep linków.

Expo Go, iOS i web otrzymują jawny status unavailable. Nie używamy fikcyjnych wyników jako prawdziwej ochrony. Benchmark syntetyczny korzysta z tego samego inference, ale nie tworzy prywatnej historii ani ostrzeżeń.

## Testy i ograniczenia odbioru

Unit tests: TTL, limity, deduplikacja, izolacja konwersacji, walidator JSON i metryki benchmarku. Instrumentation tests: prawdziwe obiekty MessagingStyle, deduplikacja normalizatora, trwała historia, retencja, migracja i usuwanie w izolowanym katalogu testowym.

Testy nie zastępują R02/R04: licencjonowany model, rzeczywiste powiadomienia komunikatorów, cold start, offline, utrata zgody, bateria i wydajność wymagają fizycznego telefonu. Te odbiory pozostają w backlogu do chwili uzyskania dowodu. Nie włączamy systemowego dostępu do prywatnych powiadomień automatycznie.
