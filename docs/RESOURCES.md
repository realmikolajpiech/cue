# Cue — zasoby i autorstwo

## Zespół i zakres prac

Undefined: Mikołaj Piech i Marcel Chudyba. Według deklaracji przekazanej przez zespół Cue powstało od zera podczas HackYeah. Istniejące komponenty poniżej stanowią bazę techniczną; nie przypisujemy sobie autorstwa ich silników, modeli ani kodu upstream. Chronologia commitów jest materiałem pomocniczym, nie samodzielnym dowodem czasu powstania każdej funkcji.

Wkład aplikacyjny: połączenie natywnych integracji z interfejsem Cue, pamięć per czat, adaptacja stylu i tonu, klawiaturowy przepływ sugestii, interpretacja oraz korekta ustaleń, źródła, backend i walidacja, demonstrator, testy i ewaluacja.

## Wykorzystane komponenty

- Expo SDK 57, React Native, Expo Router, Expo Modules oraz moduły Expo — https://docs.expo.dev/ ; wersje w package-lock.json.
- React, TanStack Query, Zustand, Zod, AsyncStorage, FlashList i Reanimated — wersje i zależności przechodnie w package-lock.json. Zachowane notices pakietów.
- Mosty komunikatorów zaadaptowane z Arie, MirrorMsg, mautrix-meta/messagix i whatsmeow. Szczegółowe repozytoria, commity, modyfikacje i licencje: [NOTICE](../modules/subtext/NOTICE.md). Nie są oficjalnymi integracjami Meta.
- HeliBoard 4.0: silnik klawiatury, słowniki, korekta, emoji i ustawienia. Adaptacja: [INTEGRATION](../vendor/heliboard/INTEGRATION.md), zachowane źródła i licencje w vendor/heliboard.
- DeepSeek API, alias konfiguracji `deepseek-flash`: generowanie oraz interpretacja kontekstu. Nie trenowaliśmy własnego modelu bazowego. https://api-docs.deepseek.com/
- Supabase Auth i Edge Functions: uwierzytelnianie, proxy do AI i limity. https://supabase.com/docs
- Fonty DM Sans i Manrope: licencje w assets/fonts.
- Zasoby brandowe Cue znajdują się w assets; plik assets/mascots/prompts.json dokumentuje prompty maskotek.

## Użycie AI i danych

Codex istotnie wspierał implementację, analizę, dokumentację, testy, przygotowanie syntetycznych rozmów oraz prezentacji. DeepSeek działa jako składnik produktu. Ewaluacja i demo używają fikcyjnych rozmów, bez prywatnych wiadomości kontaktów. Przegląd semantyczny wyników przez asystenta jest oznaczony jako taki i nie zastępuje oceny użytkowników.

Aktualne wyniki dotyczą wyłącznie Cue i znajdują się w `benchmarks/cue`.
Raporty wcześniejszego projektu zachowuje historia Git; nie należą do zgłoszenia Cue.

## Licencje przy dystrybucji

Repo zawiera różne licencje: m.in. GPL-3.0 HeliBoard, AGPL-3.0 komponentów mostów i MPL-2.0 whatsmeow, zgodnie z ich notices. Główny plik LICENSE pochodzi ze szkieletu Expo i nie opisuje sam wszystkich komponentów wynikowego APK. Do dystrybuowanego rozwiązania należy dołączyć właściwe notices i odpowiadające mu źródła; samo podanie nazwy biblioteki nie jest pełnym rozliczeniem warunków dystrybucji. Nie deklarujemy zakończonego audytu prawnego.
