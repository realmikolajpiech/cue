# Cue — architektura

## Przepływ danych

Messenger / WhatsApp → natywne mosty na urządzeniu → prywatny magazyn czatów i osobna pamięć → Supabase Edge Function → DeepSeek → walidacja po stronie Androida → zapisane ustalenia i propozycje → edytowalny tekst w klawiaturze.

Expo SDK 57 / React Native / Expo Router obsługują interfejs. Kotlin zarządza integracjami, pamięcią, cyklem życia i klawiaturą opartą na HeliBoard. Backend sprawdza anonimową sesję Supabase Auth, limity oraz format żądania. Sekret dostawcy pozostaje po stronie serwera.

## Co robi AI, a co kod aplikacji

| Funkcja | AI | Kod deterministyczny |
| --- | --- | --- |
| Ustalenia | Interpretacja treści i propozycje zmian | Walidacja typów, dat, źródeł i istniejących identyfikatorów |
| Pamięć | Przyrostowe fakty i zmiany zobowiązań | Trwały zapis, izolacja czatów, limity, ochrona ręcznych korekt |
| Styl | Generowanie tekstu zgodnego z próbkami i tonem | Liczenie cech stylu, częstych zwrotów, deduplikacja próbek |
| Daty | Interpretacja terminu w kontekście | Kalendarz względem daty wiadomości, strefa czasu, wyliczenie statusu |
| Odpowiedzi | Warianty tekstu lub propozycja nieodpisywania | Użytkownik wybiera i edytuje; aplikacja nie wysyła wiadomości |

## Pamięć i źródła

Do 150 czatów w cache, do 200 wiadomości na czat i do 4000 znaków na wiadomość. Osobna pamięć przeżywa przycięcie historii. Źródła nowych ustaleń zawierają do 8 lokalnych cytatów z autorem, datą i ID. Wpisy sprzed tej zmiany korzystają z dostępnej historii; brak źródła jest jawnie oznaczony. Cytaty nie są dodatkowo dodawane do kontekstu AI. Ręczna korekta zachowuje źródła poprzedniej analizy z wyraźnym oznaczeniem pochodzenia.

Nowe wiadomości uruchamiają automatyczną aktualizację pamięci po 3 sekundach ciszy, jeśli globalne AI jest włączone. Nie jest to automatyczna wysyłka ani automatyczne generowanie odpowiedzi. Wykluczenie czatu blokuje jego analizy, aktualizacje pamięci i użycie w ogólnym podglądzie stylu. Zmiana zgody unieważnia zapisy wyników będących w toku; nie wycofuje już wysłanego żądania.

Demo ma jeden zastrzeżony identyfikator, cztery kroki oraz osobną pamięć. Używa prawdziwego modelu wyłącznie po działaniu użytkownika. Nie trafia do statystyk ogólnego stylu ani listy osób klawiatury w zewnętrznych komunikatorach. Reset usuwa tylko dane demo.

## Dane wysyłane do AI

Do 80 ostatnich wiadomości, nadawcy, intencja/szkic, pamięć i próbki stylu danego czatu. Przy odpowiedzi: maksymalnie 3 dostępne zdjęcia z ostatnich 12 wiadomości. Automatyczna aktualizacja pamięci nie dołącza obrazów. Klucz API DeepSeek nie jest w APK. Funkcja nie zapisuje treści w bazie ani nie loguje ich; nie jest to deklaracja polityki retencji dostawcy modelu.

Wiadomości, obrazy, pamięć i szkic są danymi, nie uprawnieniami do zmiany instrukcji systemowych. Prompt ogranicza dopowiadanie faktów, diagnozowanie i manipulację, ale zabezpieczenie promptem nie jest gwarancją.

## Operacje i limity

Sprawdź migracje `supabase/migrations` dla aktualnych kwot. Tryb developerski wymaga Metro; samodzielny release zawiera JavaScript. Android może ograniczyć proces w tle. Lokalna treść znajduje się w prywatnym katalogu aplikacji bez dodatkowej warstwy szyfrowania wiadomości; tokeny i wybrane dane sesji korzystają z Keystore.

Model w kodzie ma alias `deepseek-flash`, możliwy do zmiany konfiguracją backendu. Nie deklarujemy niezweryfikowanego numeru generacji modelu. Zestaw ewaluacyjny zapisuje, czy zgodność wdrożonego promptu i modelu została potwierdzona.
