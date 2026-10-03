# Benchmark Guardian

`cases.json` zawiera 20 syntetycznych scamów i 20 hard negatives po polsku. Przypadki obejmują podszywanie się, prośby o pieniądze, OTP, presję, fałszywe autorytety, linki, poprawne pilne prośby i prompt injection. Wszystkie wiadomości są fikcyjne.

Zestaw jest testowy: nie dostrajać na nim promptu. Do strojenia utworzyć oddzielny zbiór. Ręczne etykiety to oczekiwane high / nie-high; pozostałe poziomy ryzyka nie są pełnym ground truth. Nie są to pomiary produkcyjnej skuteczności.

## Uruchomienie na telefonie

1. Zbuduj własny build Android (`npm run android -- --device`).
2. W zakładce Ochrona importuj zgodny, legalnie uzyskany model `.litertlm`.
3. W Ustawieniach wybierz „Uruchom benchmark”. Monitorowanie zostanie wyłączone na czas testu i pozostanie wyłączone po zakończeniu. Włącz je świadomie po testach.
4. Raport zapisuje się w prywatnym katalogu aplikacji `no_backup/guardian-benchmark.json`. Zawiera tylko ID syntetycznych przypadków, etykiety, enumy, czas i konfigurację; nie zapisuje swobodnych odpowiedzi modelu.
5. Odczytaj raport z development build:

```sh
adb exec-out run-as com.mikolajpiech.guardian cat no_backup/guardian-benchmark.json > benchmarks/report.local.json
```

Skrypt `node scripts/check-benchmark.mjs benchmarks/report.local.json` sprawdza kompletność raportu. Raport lokalny jest ignorowany przez Git. W repo można zapisać zweryfikowany raport ze świadomie podanymi metadanymi testowego urządzenia.

## Metryki

Precision / recall / false positive rate dotyczą wykryć high. `uncertain` dla scamu liczy się do FN, a nie do sukcesu. Przy braku przewidzianych high precision jest null. `jsonValidity` mierzy odsetek odpowiedzi przyjętych przez walidator; błąd modelu lub timeout daje uncertain i valid=false. p50/p95 są mierzone zegarem monotonicznym. `peakObservedPssKb` to najwyższa próbka PSS po przypadku, nie gwarantowany peak całej inferencji. Wartość należy uzupełnić profilingiem Androida.

Powtórz test po restarcie aplikacji i w trybie samolotowym. Zanotuj model SHA-256, backend, wersję runtime i promptu. Testy GPU/CPU i inne modele wykonuj oddzielnie. Wyniki nie powstaną, jeśli model nie został zaimportowany — brak danych nie jest wynikiem zerowym.
