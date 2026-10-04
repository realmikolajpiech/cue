# Cue — ewaluacja pamięci i odpowiedzi

Zestaw: 24 fikcyjne rozmowy po polsku. Kategorie: kierunek zobowiązania, daty, niepewność, zmiana terminu, odwołanie, częściowy i pełny zwrot, nierozstrzygnięte wykonanie, zamknięty/nowy wątek, brak danych, wsparcie, flirt, granice, prompt injection, szkic jako niefakt, niedostępne zdjęcie i własny styl. Dziewięć przypadków ma dodatkowy wariant bez pamięci: 33 wywołania w pełnej serii.

To **zestaw regresyjny przygotowany ze znajomością produktu i promptu**, a nie niezależny zbiór testowy ani badanie populacyjne. Nie służy do ogłaszania procentowej „skuteczności AI”. Po poprawkach potrzebne są także nowe, niewidziane wcześniej przypadki.

## Uruchomienie

```sh
npm run benchmark:cue
npm run test:cue
node scripts/cue/evaluate.mjs --live --out benchmarks/cue/report-next.json
```

Tryb bez `--live` sprawdza tylko zestaw i nie wywołuje modelu. Tryb live tworzy jedną anonimową sesję Supabase, używa tego samego endpointu co aplikacja i wysyła wyłącznie fikcyjne przypadki. Tokeny pozostają w RAM. W razie limitu runner kończy serię, nie tworzy kolejnych tożsamości. Publiczną konfigurację można nadpisać przez CUE_EVAL_URL i CUE_EVAL_PUBLISHABLE_KEY; nie przekazuj sekretu DeepSeek.

Każdy raport zawiera skrót danych i lokalnego promptu, commit, czas, status HTTP, surowy wynik syntetyczny i sprawdzenia. Hash lokalnego promptu nie dowodzi zgodności wdrożenia. Nie przedstawiaj aliasu skonfigurowanego modelu jako zweryfikowanej wersji dostawcy.

## Pierwsza rzeczywista seria, 4 października 2026

Źródło: [report.json](report.json). Wyniki zachowane również wtedy, gdy wykryły problem.

| Pomiar | Z pamięcią | Bez pamięci |
| --- | --- | --- |
| Próby | 24 | 9 |
| Udane żądania | 23 | 9 |
| Poprawny podstawowy kontrakt JSON | 23 | 9 |
| ID źródeł należą do wejścia | 23 | 9 |
| Zaliczone częściowe sprawdzenia automatyczne | 23 | 7 |
| p50 udanych wywołań endpointu | 2,573 s | 2,655 s |
| p95 udanych wywołań endpointu | 3,688 s | 3,272 s |

Kolumny mają różne liczby przypadków: nie porównuj ich bezpośrednio jako ogólnej przewagi jakości. W dziewięciu sparowanych przypadkach wariant z pamięcią zaliczył 9 częściowych sprawdzeń, bez pamięci 7. Różnica dotyczy m.in. aktualizacji wcześniej istniejących rekordów; to oczekiwany efekt dostarczenia identyfikatora, nie niezależny dowód lepszej konwersacji.

Jeden przypadek `draft-not-fact` zwrócił HTTP 502 / invalid_response. Błędy pozostają w mianowniku. Czasy obejmują żądanie do gatewaya; nie obejmują synchronizacji, klawiatury i renderowania telefonu. Percentyle obejmują tylko udane żądania i są niestabilne przy tak małej próbce.

## Wykryte ograniczenia

[Przegląd semantyczny](review.json) wykonał asystent, nie użytkownicy. Nie liczymy go jako badania z uczestnikami.

- Poprawne ID nie zapobiegły dopowiedzeniu „nie widziałem filmu” bez dowodu.
- Sporny dług miał błędny kierunek owner mimo poprawnego statusu tentative.
- Niektóre warianty dodawały obietnice lub zbyt szorstki ton.
- Brak pamięci czasem pojawiał się jako techniczny komentarz w tekście do wysłania.
- Przy zamknięciu model zwrócił pusty text; stary zapis natywny odrzucał taką aktualizację. W tej iteracji poprawiono zapis znanego wpisu i dodano test regresji. Pierwsza seria nie potwierdzała kompletnego przepływu aplikacji.

Lokalny prompt doprecyzowano po tej serii. Dopiero potwierdzone wdrożenie i nowy raport mogą potwierdzić efekt. Nie nadpisuj pierwszego raportu lepszymi wynikami. Badaj osobno poprawność pamięci, naturalność tekstu, bezpieczeństwo i opóźnienie.

## Kolejny etap

[Protokół próby z użytkownikami](../../docs/USER-STUDY.md). Uczestnicy oceniają naturalność i potrzebne poprawki; obecnie nie ma takich wyników. Testy Kotlin sprawdzają zapis i walidację, nie zastępują oceny modelu.
