# Cue

Klawiatura AI, która korzysta z kontekstu dostępnych rozmów i pomaga znaleźć własne słowa — w codziennej pogawędce, trudnej rozmowie czy flircie. Pamięta szczegóły i ustalenia, uwzględnia Twój styl i intencję; Ty sprawdzasz, edytujesz i wysyłasz odpowiedź.

**HackYeah · Undefined · Mikołaj Piech i Marcel Chudyba.**
[Opis produktu](docs/PRODUCT.md) · [Prezentacja PDF](artifacts/cue/Cue-HackYeah.pdf) · [Demo](docs/DEMO.md) · [Ewaluacja Cue](benchmarks/cue/README.md) · [Zasoby i autorstwo](docs/RESOURCES.md)
Nie wymaga Matrixa, VPS-a ani włączonego Maca. Internet jest potrzebny do
komunikatorów i AI. Wersja release zawiera JavaScript i nie wymaga Metro.

## Uruchomienie

Wymagane: Node 22.13+, Android SDK, JDK 17 (lub sprawdzony lokalnie JDK 21) i telefon Android **arm64-v8a**.
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

1. W onboardingu możesz włączyć **analizę w chmurze** oraz **klawiaturę Cue**.
   Przełącznik AI zapisuje wybór od razu; ekran informuje o wysyłaniu danych do DeepSeek.
   Klawiaturę włącz w otwartej liście klawiatur Androida, wróć do Cue,
   wybierz ją do pisania i wypróbuj w polu testowym. Obie opcje możesz pominąć
   i skonfigurować później w ustawieniach. Na końcu otwórz **Połączenia**.
2. Messenger: zaloguj się w natywnym ekranie Facebooka. Dokończ ewentualne
   potwierdzenie logowania we własnej aplikacji Facebook.
3. WhatsApp: podaj numer z kodem kraju; w WhatsAppie użyj opcji połączenia
   urządzenia za pomocą numeru telefonu i wpisz wyświetlony kod.
4. Jeśli pominąłeś analizę w onboardingu, włącz ją w **Ustawieniach**.
   Klucz AI jest po stronie serwera.
5. Otwórz czat w **Rozmowach**. Po globalnym włączeniu AI pamięć aktualizuje się automatycznie po nowych wiadomościach. W profilu Androida możesz wykluczyć czat z AI. „Uzupełnij” uruchamia analizę ręcznie; źródła i korekty są przy zapisanych sprawach.
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
liczniki: 500 prób analizy dziennie na sesję i 1000 dla projektu; liczniki
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
  samej nazwie nie łączymy automatycznie. Bieżący przepływ obsługuje rozmowy prywatne; grupy są pomijane.
- Lokalnie: maks. 150 rozmów, po 200 wiadomości, do 4000 znaków na wiadomość
  i ostatni profil. Magazyn jest w prywatnym `noBackupFilesDir`; bez dodatkowej
  warstwy szyfrowania treści. Messenger szyfruje cookies w Keystore; WhatsApp
  i stan E2EE używają prywatnych baz mostów.
- Każdy prywatny czat ma trwałą pamięć w `subtext-person-memory.json`, osobną
  dla Messengera i WhatsAppa. Przeżywa restart, skrócenie historii do 200 wiadomości
  i usunięcie nieaktywnego czatu z cache 150 rozmów. Odłączenie konta lub
  wyczyszczenie historii usuwa też pamięć. Demo ma osobną pamięć syntetycznej rozmowy i nie wpływa na ogólny styl.
- Synchronizacja dopisuje tylko nowe ID wiadomości: liczy własne próbki,
  częste zwroty (co najmniej 3 użycia), cechy stylu z ostatnich 60 próbek
  i zachowuje autentyczne przykłady, w tym dłuższe odpowiedzi. Okno deduplikacji
  obejmuje do 4096 skrótów ID; po jego skróceniu starsze wiadomości są pomijane
  według progu czasu, aby ponowna synchronizacja nie zawyżała statystyk.
- Po włączeniu AI pamięć aktualizuje się po nowych wiadomościach i około 3 sekundach ciszy. Czat wykluczony z AI jest pomijany. Błąd zachowuje poprzednią pamięć; minimalny odstęp ponowienia wynosi minutę. Limity kosztu są po stronie backendu, a błąd limitu wstrzymuje próby do kolejnego dnia UTC.
- Analiza odpowiedzi jest na żądanie. Do DeepSeek trafia do 80 ostatnich
  zapisanych wiadomości, szkic i pamięć wyłącznie tego czatu; przy odpowiedzi także maks. 3 dostępne zdjęcia z ostatnich 12 wiadomości. Zmiany kontekstu
  są przyrostowe, z ID dowodów; brak zmian nie usuwa poprzednich wpisów.
  Model: `deepseek-flash`, JSON output, wyłączony tryb thinking.
- Profile: podsumowanie, obserwacje i ustalenia z ID źródłowych wiadomości,
  przypomnienie przed odpowiedzią, od jednego do trzech wariantów, w tym możliwość nieodpisywania. Źródła nowych wpisów zachowują cytat, autora i datę; ręczne korekty są oznaczone. Nie są diagnozą
  osobowości. Walidator odrzuca twierdzenia bez dostępnych źródeł; obecność
  źródła nie gwarantuje trafnej interpretacji.
- Klawiatura wymaga jawnego wyboru rozmowy. Nie odczytuje ekranu Messengera
  przez accessibility. Sugestie są wyłączone w polach haseł i bez personalizacji.
- Ta iteracja ma odbiór Androida. Równolegle rozwijana implementacja iOS wymaga osobnej weryfikacji; web i Expo Go nie obsługują natywnych integracji.
- Integracje są nieoficjalne, a dostępna historia zależy od usługi i sesji.
  Aplikacja nie gwarantuje pobrania całego archiwum ani ciągłości po force-stop.

## Weryfikacja

```sh
npm run lint
npm run typecheck
npm run test:cue
npm run benchmark:cue
./android/gradlew -p android :subtext:testDebugUnitTest
./android/gradlew -p android :subtext:connectedDebugAndroidTest
```

Pełny odbiór wymaga logowania użytkownika do obu komunikatorów i próby
rzeczywistej synchronizacji, wygaśnięcia sesji, powrotu z tła oraz klawiatury.
Testy nie wysyłają wiadomości do kontaktów.

Repo zawiera wyłącznie obecny produkt Cue. Poprzedni projekt, lokalny model
i jego benchmarki można odtworzyć z historii Git oraz brancha
`codex/backup-before-sync-20261003`.
Techniczne identyfikatory `com.mikolajpiech.guardian`, slug i scheme `guardian`
zachowano dla zgodności z zainstalowaną aplikacją i jej danymi.

### Klawiatura HeliBoard

Klawiatura Cue korzysta z HeliBoard 4.0: silnika pisania, polskiego słownika,
autokorekty, emoji, schowka i ustawień wyglądu. Nad klawiaturą znajduje się
panel podpowiedzi Cue. Pierwsze uruchomienie wybiera polski układ i obramowania
klawiszy; kolejne uruchomienia zachowują ustawienia użytkownika.

Kod i licencje są w `vendor/heliboard`, a szczegóły adaptacji w
`vendor/heliboard/INTEGRATION.md`. Integracja jest odtwarzana przez config plugin
`withSubtext` przy `expo prebuild`. Wymaga nowego natywnego buildu Androida.
