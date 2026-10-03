# Guardian — zasoby i pochodzenie

- Expo SDK 57, Expo Router, Expo Modules API, Expo Dev Client, Expo Document Picker, Expo Symbols — https://docs.expo.dev/ (MIT; zweryfikować notice pakietów przy wydaniu).
- React / React Native — https://reactnative.dev/ (MIT).
- LiteRT-LM Android 0.15.0 — https://github.com/google-ai-edge/LiteRT-LM (Apache 2.0). Nie używamy ruchomego `latest.release`.
- Kandydat modelu Gemma 3 1B — https://huggingface.co/litert-community/Gemma3-1B-IT, licencja Gemma: https://ai.google.dev/gemma/terms. Artefakt nie jest dołączony ani pobierany bez zaakceptowania warunków przez uprawnionego użytkownika.
- Android NotificationListenerService — https://developer.android.com/reference/android/service/notification/NotificationListenerService.
- Zustand, TanStack Query, Zod, AsyncStorage, FlashList — zewnętrzne biblioteki; wersje i pełna lista dependencies w `package-lock.json`. Zachować licencje i notices dystrybuowanych zależności.
- Referencja wzorców UI: Google AI Edge Gallery https://github.com/google-ai-edge/gallery — ekran zarządzania lokalnym modelem i jawne informacje o runtime. Nie kopiujemy zasobów graficznych Gallery.
- Benchmark `benchmarks/cases.json`: autorski, syntetyczny zestaw polskich scenariuszy przygotowany przy wsparciu Codex. Nie zawiera autentycznych prywatnych wiadomości; 40 przykładów nie stanowi walidacji produkcyjnej.
- Znaczące wsparcie AI: Codex wspierał dokumentację, implementację i testy w tym repo. Zespół powinien ujawnić również inne narzędzia/model/API wykorzystane poza tym czatem.

Repo powstało ze szkieletu Expo i zawiera historię migracji z pustego projektu Android Studio. Granicę prac przed i podczas hackathonu ustala zespół na podstawie faktycznej daty rozpoczęcia eventu; nie należy jej zgadywać z dat commitów.
