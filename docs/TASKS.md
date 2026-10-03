# Guardian — zadania implementacyjne

Aktualizacja: 3 października 2026. `[x]` oznacza fundament zaimplementowany; `[ ]` oznacza pracę do wykonania. Demo UI nie oznacza działającej ochrony. Zadania prowadzą od obecnego projektu do MVP opisanego w [dokumencie produktu](PRODUCT.md).

## P0 Fundament projektu

- [x] F01 Rozbudować istniejący Expo SDK 57: TypeScript strict, Router w `src/app`, aliasy, lint, kontrola wersji dependencies i lockfile.
- [x] F02 Dodać onboarding z usunięciem ze stosu po zakończeniu, zakładki Ochrona / Ostrzeżenia / Ustawienia i szczegóły wyniku.
- [x] F03 Dodać semantyczne tokeny jasnego i ciemnego motywu, komponenty, dostępne cele dotyku i symbole platformowe.
- [x] F04 Przygotować schemat wyniku, słownik ryzyk, kategorie, sygnały i wyraźnie oznaczone fixtures demonstracyjne.
- [x] F05 Wydzielić status mostu Android, jawny stan braku silnika, Zustand z zapisanym onboardingiem oraz TanStack Query do statusu.
- [x] F06 Dodać lokalny moduł Expo Kotlin: status dostępu, otwarcie ustawień i punkt integracji inference; bez zbierania wiadomości przed implementacją silnika.
- [x] F07 Zapisać produkt, architekturę, kontrakt, backlog i instrukcję uruchomienia w repo.

Odbiór fundamentów: lint, typecheck, Expo Doctor, export Android i web, smoke UI. Pełny test native i pomiar wydajności nie są zastępowane eksportem bundla.

## P0 Sprawdzenie największych ryzyk

- [ ] R01 Zweryfikować oryginalny załącznik challenge'u i stworzyć mapę wymaganie → demonstracja → dowód. Nie wyciągać wymagań z domysłów. Odbiór: lista wymaganych kryteriów z oryginalnym źródłem.
- [ ] R02 Uruchomić Gemma 3 1B przez LiteRT-LM na fizycznym Androidzie, dobrać zgodny artefakt i przypiąć wersję biblioteki. Odbiór: offline inference po restarcie, pomiar RAM i czasu, znana licencja. Jeśli jakość za niska, sprawdzić Gemma 3n E2B / inną małą rodzinę.
- [ ] R03 Zbudować benchmark 20 scamów + 20 hard negatives po polsku. Odbiór: raport precision / recall / false positives / uncertain / JSON validity; zapisany prompt i model. Nie dostrajać na zbiorze testowym.
- [ ] R04 Sprawdzić NotificationListenerService dla SMS, WhatsApp, Messenger. Odbiór: rzeczywiste sample formatów bez prywatnych logów, lista ograniczeń i skuteczne grupowanie rozmów.

R02 i R04 są niezależne technicznie. Warstwa UI może rozwijać się bez nich dzięki fixtures; działająca ochrona wymaga obu.

## P0 Pipeline Android

- [ ] N01 Dodać usługę listenera i deklarację manifestu w module. Zgoda ma pochodzić z ustawień systemowych po objaśnieniu. Odbiór: denied / granted / revoked, ponowne połączenie i restart procesu.
- [ ] N02 Normalizować MessagingStyle, tekst i aktualizacje powiadomień; odrzucać własne i grupowe summary. Odbiór: brak duplikatów i brak łączenia obcych konwersacji.
- [ ] N03 ConversationBuffer: 5 wiadomości, TTL 15 min, tylko RAM, limity liczby konwersacji i rozmiaru. Odbiór: testy TTL z kontrolowanym zegarem, limitów, pauzy, deduplikacji i czyszczenia.
- [ ] N04 Implementować GuardianInference z LiteRT-LM poza wątkiem UI: serializowana kolejka, timeout, cancel, lifecycle engine i fallback sprzętowy. Zależności: R02, N03.
- [ ] N05 Walidować odpowiedź modelu i mapować wyłącznie enumy do bezpiecznych lokalnych wyjaśnień. Odbiór: malformed JSON, nieznana kategoria, PII w output, prompt injection nie trafiają jako swobodny tekst do JS.
- [ ] N06 Przesyłać zdarzenia wyniku i statusu przez most; synchronizować po wznowieniu UI. Odbiór: wyłącznie kontrakt, żadnych raw messages, znaczników kontaktu ani cytatów.
- [ ] N07 Dodać trwały lokalny magazyn minimalnych wyników z retencją i usuwaniem. Odbiór: restart, limit historii, migracja schematu, delete history bez ponownego przywrócenia.
- [ ] N08 Dodać systemowe ostrzeżenie z kanałem, zgodą na notifications, deep linkiem i rate limit. Zależności: N05–N07. Odbiór: cold start, duplicate events, foreground/background i odmowa zgody.

## P1 Konfiguracja i użyteczny produkt

- [ ] U01 Ekran konfiguracji: instrukcja dostępu, powrót z settings, rzeczywisty refresh stanu. Status „aktywna” tylko przy listener connected + model ready + monitoring enabled.
- [ ] U02 Model manager: licencja, wolne miejsce, checksum, atomowy zapis, postęp, retry i anulowanie; obsłużyć offline i uszkodzony plik. Zależność: R02.
- [ ] U03 Połączyć dashboard i historię z wynikami native, oddzielić demo od realnych danych. Odbiór: empty / loading / error / ready / paused / uncertain.
- [ ] U04 Wybrać aplikacje do monitorowania, pauza i wznowienie; natychmiast czyścić bufor po pauzie lub cofnięciu zgody.
- [ ] U05 Zapisywać lokalny feedback „sprawdzone” i „błędne ostrzeżenie” bez automatycznego zmieniania oceny modelu.
- [ ] U06 Sprawdzić TalkBack, XL font, contrast, small screen, light/dark, Android Back i powroty z ustawień. Nagrać pełny flow i sprawdzić ruch na release build.

## P1 Demo hackathonowe

- [ ] D01 Przygotować syntetyczne powiadomienia scenariusza podszywania się pod rodzinę oraz poprawnej rozmowy. Odbiór: deterministyczny scenariusz wejścia; wynik pochodzi z rzeczywistego lokalnego modelu.
- [ ] D02 Pokazać airplane mode, powiadomienie, kontekst, ostrzeżenie i wyjaśnienie; oddzielić działającą ścieżkę od demo UI.
- [ ] D03 Przygotować 3-minutowy pitch: problem, lokalna architektura, demo, pomiary, ograniczenia, następny krok. Zależności: R01–R04, N01–N08.
- [ ] D04 Development / preview APK i fizyczny smoke test przed prezentacją. Nie włączać cloud jako ukrytego obejścia awarii local inference.

## P2 Po MVP

- [ ] P01 Enhanced AI Analysis: osobna zgoda i opis zakresu, redakcja PII z testami, backend bez zapisu rozmów, limity i timeout. Dopiero po przeglądzie prywatności i jakości redakcji.
- [ ] P02 Rozbudowany benchmark na różnych telefonach, bateria, wielojęzyczność i odporność na nowe formy manipulacji.
- [ ] P03 Zaprojektować alternatywne wejście na iOS; nie zakładać dostępu do powiadomień innych aplikacji.
- [ ] P04 Rozstrzygnąć model dystrybucji, aktualizacji modeli i ewentualny model biznesowy. Rozmowa nie ustala monetyzacji.

## Proponowana kolejność najbliższych prac

1. R01, R02 i R04: zweryfikować wymagania, lokalny model i dostępne dane.
2. R03 oraz N01–N05: ocenić jakość i zbudować pipeline bez UI.
3. N06–N08 i U01–U05: połączyć wyniki, stan ochrony i ostrzeżenia.
4. U06 i D01–D04: przetestować pełny produkt i przygotować demo.

Najpierw dowód, że model rozumie polski kontekst na docelowym telefonie. Sam dopracowany dashboard nie usuwa tego ryzyka.
