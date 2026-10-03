# Cue

Androidowy asystent komunikacji: Messenger i WhatsApp na urządzeniu, pamięć
rozmów, profile oparte na wiadomościach i podpowiedzi DeepSeek V4.1 Flash.
Nie wymaga Matrixa, VPS-a ani włączonego Maca. Internet jest potrzebny do
komunikatorów i AI. Wersja release zawiera JavaScript i nie wymaga Metro.

## Uruchomienie

Wymagane: Node 22.13+, Android SDK, JDK 17 i telefon Android **arm64-v8a**.
Na Macu `npm run android` wybiera zainstalowany Homebrew JDK 17 i wykrywa SDK
w `~/Library/Android/sdk`, jeśli `ANDROID_HOME` nie jest ustawione.
Repo zawiera AAR z projektu Arie. Szczegóły i źródła: [NOTICE](modules/subtext/NOTICE.md).

```sh
npm ci
npm run android -- --device SM_S931B
```

Samodzielny APK (kompilacja na Macu lub w EAS; runtime pozostaje na telefonie):

```sh
npm run android -- --variant release --device SM_S931B --no-bundler
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
4. W **Ustawieniach** włącz analizę w chmurze. Klucz AI jest po stronie serwera.
5. Otwórz rozmowę w **Osobach**, przejrzyj wiadomości i uruchom analizę.
6. Opcjonalnie włącz klawiaturę Cue w ustawieniach Androida. Podczas
   pisania wybierz właściwą rozmowę, poproś o sugestię i dotknij jej, aby
   wstawić tekst. Aplikacja nie wysyła odpowiedzi automatycznie.

Klucz DeepSeek przechowuj wyłącznie w Supabase → Edge Functions → Secrets,
pod nazwą `DEEPSEEK_API_KEY`. Aplikacja używa publicznego klucza projektu
oraz automatycznej anonimowej sesji Supabase Auth; włącz **Allow anonymous
sign-ins** w Authentication → Sign In / Providers. Tokeny sesji urządzenia
są szyfrowane przez Android Keystore. Sesje nie służą do zapisu rozmów.

Backend: `supabase/functions/deepseek-analyze`. Weryfikuje sesję, ogranicza
rozmiar żądania i wywołuje DeepSeek. Rozmowy i odpowiedzi nie są zapisywane
w bazie ani logowane przez funkcję. Prywatna tabela przechowuje wyłącznie
liczniki: 20 prób analizy dziennie na sesję i 200 dla projektu; liczniki
starsze niż 7 dni są czyszczone przy kolejnych wywołaniach. Limity obejmują
również nieudane wywołania dostawcy. Globalny limit zabezpiecza koszt także
przy tworzeniu kolejnych anonimowych sesji; nie zastępuje ochrony przed DoS.
Opcjonalny sekret `DEEPSEEK_MODEL` zmienia model (domyślnie `deepseek-flash`).

Wdrożenie po zalogowaniu do CLI:

```sh
supabase link --project-ref qajdybynwafehizuaxad
supabase db query --linked --file supabase/migrations/20261003173000_ai_quota.sql
supabase functions deploy deepseek-analyze --project-ref qajdybynwafehizuaxad --use-api
```

`verify_jwt = false` wyłącza starszą walidację bramki; funkcja sama sprawdza
token użytkownika przez Supabase Auth. Klucz publiczny sam nie daje dostępu
do płatnych analiz. Nie dodawaj sekretów do repozytorium ani APK.

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
