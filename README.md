# Cue

Androidowy asystent komunikacji: Messenger i WhatsApp na urządzeniu, pamięć
rozmów, profile oparte na wiadomościach i podpowiedzi DeepSeek V4.1 Flash.
Nie wymaga Matrixa, VPS-a ani włączonego Maca. Internet jest potrzebny do
komunikatorów i AI. Wersja release zawiera JavaScript i nie wymaga Metro.

## Uruchomienie

Wymagane: Node 22.13+, Android SDK, JDK 21 i telefon Android **arm64-v8a**.
Repo zawiera AAR z projektu Arie. Szczegóły i źródła: [NOTICE](modules/subtext/NOTICE.md).

```sh
npm ci
npx expo prebuild --platform android --no-install
npx expo run:android --device
```

Samodzielny APK (kompilacja na Macu lub w EAS; runtime pozostaje na telefonie):

```sh
npx expo run:android --variant release --device --no-bundler
```

W Android Studio otwieraj wygenerowany katalog `android/`, nie główny katalog
repo. Własny kod native jest w `modules/subtext`, konfiguracja w `app.json`
i `plugins/withSubtext.js`; nie edytuj wygenerowanego projektu ręcznie.

## Pierwsze użycie

1. Przejdź onboarding i otwórz **Połączenia**.
2. Messenger: zaloguj się w natywnym ekranie Facebooka. Dokończ ewentualne
   potwierdzenie logowania we własnej aplikacji Facebook.
3. WhatsApp: podaj numer z kodem kraju; w WhatsAppie użyj opcji połączenia
   urządzenia za pomocą numeru telefonu i wpisz wyświetlony kod.
4. W **Ustawieniach** wpisz klucz DeepSeek i włącz analizę w chmurze.
5. Otwórz rozmowę w **Osobach**, przejrzyj wiadomości i uruchom analizę.
6. Opcjonalnie włącz klawiaturę Cue w ustawieniach Androida. Podczas
   pisania wybierz właściwą rozmowę, poproś o sugestię i dotknij jej, aby
   wstawić tekst. Aplikacja nie wysyła odpowiedzi automatycznie.

Klucz nie jest zapisany w kodzie ani APK. Pole ustawień zapisuje go zaszyfrowanego
Android Keystore. Do deweloperskiego buildu można dostarczyć go przez
`node scripts/provision-subtext-key.mjs`, z ignorowanego `.env.subtext.local`.
Nigdy nie dodawaj tego pliku do Gita.

## Architektura i zakres

- Expo SDK 57 + Expo Router: onboarding, lista rozmów, profil, połączenia,
  ustawienia; TanStack Query i walidacja kontraktów Zod.
- `modules/subtext`: Kotlin, biblioteki Go z Arie/MirrorMsg, lokalny zapis,
  połączenia, DeepSeek i `InputMethodService`.
- Messenger korzysta z messagix/mautrix-meta, WhatsApp z whatsmeow.
  Przechowywane sesje są odtwarzane po ponownym uruchomieniu.
- Foreground service utrzymuje połączenia i próbuje je wznawiać. Android może
  ograniczyć działanie w tle; force-stop wymaga ponownego otwarcia aplikacji.
- Każda rozmowa ma osobny identyfikator zawierający komunikator. Osób o tej
  samej nazwie nie łączymy automatycznie. Grupę opisujemy jako rozmowę grupową.
- Lokalnie: maks. 150 rozmów, po 200 wiadomości, do 4000 znaków na wiadomość
  i ostatni profil. Magazyn jest w prywatnym `noBackupFilesDir`; bez dodatkowej
  warstwy szyfrowania treści. Messenger szyfruje cookies w Keystore; WhatsApp
  i stan E2EE używają prywatnych baz mostów.
- Analiza jest jawna i wykonywana na żądanie, nie automatyczna dla wszystkich
  kontaktów. Do DeepSeek trafia do 80 ostatnich zapisanych wiadomości i szkic.
  Model: `deepseek-flash`, JSON output, wyłączony tryb thinking.
- Profile: podsumowanie, obserwacje i ustalenia z ID źródłowych wiadomości,
  przypomnienie przed odpowiedzią, trzy warianty odpowiedzi. Nie są diagnozą
  osobowości. Walidator odrzuca twierdzenia bez dostępnych źródeł; obecność
  źródła nie gwarantuje trafnej interpretacji.
- Klawiatura wymaga jawnego wyboru rozmowy. Nie odczytuje ekranu Messengera
  przez accessibility. Sugestie są wyłączone w polach haseł i bez personalizacji.
- iOS/web/Expo Go pokazują stan niedostępności zamiast fikcyjnych połączeń.
- Integracje są nieoficjalne, a dostępna historia zależy od usługi i sesji.
  Aplikacja nie gwarantuje pobrania całego archiwum ani ciągłości po force-stop.

## Weryfikacja

```sh
npm run lint
npm run typecheck
./android/gradlew -p android :subtext:testDebugUnitTest
./android/gradlew -p android :subtext:connectedDebugAndroidTest
```

Pełny odbiór wymaga logowania użytkownika do obu komunikatorów i próby
rzeczywistej synchronizacji, wygaśnięcia sesji, powrotu z tła oraz klawiatury.
Testy nie wysyłają wiadomości do kontaktów.

Stare pliki Guardiana w `src/features` i `docs` są materiałem historycznym.
Jego moduł native ma wyłączone autolinkowanie; stary lokalny model nie jest
częścią nowego przepływu. Kopia poprzedniego projektu jest na branchu
`codex/backup-before-sync-20261003`.

### Klawiatura HeliBoard

Klawiatura Cue korzysta z HeliBoard 4.0: silnika pisania, polskiego słownika,
autokorekty, emoji, schowka i ustawień wyglądu. Nad klawiaturą znajduje się
panel podpowiedzi Cue. Pierwsze uruchomienie wybiera polski układ i obramowania
klawiszy; kolejne uruchomienia zachowują ustawienia użytkownika.

Kod i licencje są w `vendor/heliboard`, a szczegóły adaptacji w
`vendor/heliboard/INTEGRATION.md`. Integracja jest odtwarzana przez config plugin
`withSubtext` przy `expo prebuild`. Wymaga nowego natywnego buildu Androida.
