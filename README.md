# Guardian

Aplikacja Expo na Androida do lokalnej analizy oznak manipulacji w powiadomieniach.
Onboarding, konfiguracja, status native i historia są zaimplementowane. Gemma 3 1B
INT4 działa w Kotlinie z lokalnym LiteRT-LM, weryfikacją pliku i ograniczeniem
odpowiedzi do JSON Schema. Bez modelu aplikacja działa z nieaktywną ochroną.
Instalację opisuje [GEMMA.md](docs/GEMMA.md); pełny odbiór ochrony wymaga testów
rzeczywistych powiadomień na telefonie.

## Uruchomienie na podłączonym Androidzie

Wymagany Node.js 22.13+, Android SDK i JDK 21 (sprawdzony w lokalnym buildzie). Telefon musi mieć włączone debugowanie USB.
Polecenia wykonuj w głównym katalogu repo.

```sh
npm ci
npm run android -- --device
```

Wybierz telefon podłączony do Maca. To własny build Guardian, bez Expo Go.
Po pierwszej kompilacji, do zmian tylko w TypeScript:

```sh
npm start -- --dev-client
```

Gdy połączenie USB z Metro wymaga przekierowania:

```sh
adb reverse tcp:8081 tcp:8081
```

Kod Kotlin i zmiany config pluginów wymagają ponownego `npm run android -- --device`.
Katalog `android/` jest generowany; własny kod znajduje się w `modules/guardian`.

## Sprawdzenie projektu

```sh
npm run lint
npm run typecheck
npm run doctor
```

Po wygenerowaniu projektu native można uruchomić testy:

```sh
./android/gradlew -p android :guardian:testDebugUnitTest
ANDROID_SERIAL=<serial telefonu> ./android/gradlew -p android :guardian:connectedDebugAndroidTest
```

## Struktura

- `src/app` — Expo Router: onboarding, zakładki, szczegóły analizy.
- `src/components` — współdzielone komponenty light/dark.
- `src/features` — preferencje; `src/services` — most native i odczyty Query.
- `src/types` — walidacja kontraktu Zod.
- `modules/guardian` — Android listener, RAM buffer, lokalne wyniki i integracja Gemma/LiteRT-LM.
- `benchmarks` — 40 syntetycznych przypadków i instrukcja pomiarów.
- [Produkt](docs/PRODUCT.md), [architektura](docs/ARCHITECTURE.md), [pozostałe taski dla dwóch osób](docs/TASKS.md).
- [Zgodność z challenge](docs/CHALLENGE.md), [zasoby i licencje](docs/RESOURCES.md).

Guardian nie włącza zgód systemowych automatycznie. Surowe wiadomości nie trafiają do JS.
Expo Go, iOS i web nie obsługują Androidowego listenera. Nie ma cloud fallbacku ani modelu w repo.
