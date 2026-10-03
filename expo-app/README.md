# Guardian

Szkielet aplikacji Expo do lokalnej ochrony przed manipulacją w wiadomościach.
Obecny zakres: Expo SDK 57, TypeScript strict, Expo Router, podstawowy motyw
light/dark i jeden ekran startowy. Funkcje produktu nie są jeszcze zaimplementowane.

## Uruchomienie

Wymagany Node.js 22.13 lub nowszy. Polecenia wykonuj w katalogu `expo-app`.

```sh
npm ci
npm start
```

`npm run android`, `npm run ios` i `npm run web` uruchamiają odpowiedni podgląd.
Expo Go wystarcza dla obecnego szkieletu. Planowany moduł Kotlin i lokalny LLM
będą wymagały development build na Androidzie.

## Sprawdzenie projektu

```sh
npm run lint
npm run typecheck
npm run doctor
```

## Struktura

- `src/app` — routing i ekrany.
- `src/components` — współdzielone komponenty.
- `src/features` — przyszłe funkcje produktu.
- `src/services` — przyszłe integracje.
- `src/theme` — kolory i odstępy.
- `src/types` — przyszłe wspólne typy.
- [Dokument produktu](docs/PRODUCT.md) — założenia, zakres, architektura i ograniczenia.
- [Taski](docs/TASKS.md) — priorytety, zależności i kryteria odbioru.

Projekt Android Studio został usunięty. Docelowy kod Kotlin powstanie jako lokalny
moduł Expo; katalogów native generowanych przez Expo nie edytujemy ręcznie.
