# Guardian — zgodność z challenge Artificial Intelligence

Zweryfikowano 3 października 2026 na podstawie przekazanego przez użytkownika PDF `Details - ARTIFICIAL INTELLIGENCE.pdf`, strony 1–4. Źródło lokalne: `/Users/mikolaj/Downloads/Details - ARTIFICIAL INTELLIGENCE.pdf`. PDF nie jest automatycznie publikowany w repo. Poniższa mapa opisuje wymagania i plan dowodów; nie deklaruje, że demonstracja produktu została już odebrana.

## Wymagania i dowody

| Wymaganie z PDF | Realizacja Guardian | Dowód do pokazania | Stan |
| --- | --- | --- | --- |
| AI ma istotną rolę i odpowiadać na konkretną potrzebę (s. 1) | Lokalny model analizuje kontekst manipulacji zamiast sztywnych słów kluczowych | Rzeczywista analiza syntetycznej rozmowy przez model | Integracja zaimplementowana; potrzebny licencjonowany artefakt i benchmark |
| Pokazać współpracę komponentów i korzyść (s. 1) | Listener → bufor RAM → LiteRT-LM → walidacja enumów → wynik/ostrzeżenie → Expo | Diagram i demo wielowiadomościowego scenariusza | Kod i architektura dostępne; realny flow do odbioru |
| Wyjaśnić decyzje, możliwości i ograniczenia (s. 1) | Dane nie opuszczają Kotlin; enumy zamiast swobodnych odpowiedzi; wyraźne unavailable/uncertain | `PRODUCT.md`, `ARCHITECTURE.md`, raport benchmarku | Dokumentacja dostępna; pomiary oczekują na model |
| Użytkownik weryfikuje wynik i pozostaje w kontroli (s. 1) | Przesłanki i zalecenia, świadoma zgoda, pauza, historia i usuwanie | Ekrany konfiguracji, analizy i ustawień | Implementacja dostępna; pełny test zgód do wykonania |
| Konkretny use case (s. 1) | Podszywanie się pod rodzinę: zmiana numeru + presja czasu + przelew | Syntetyczne powiadomienia i lokalna analiza offline | Zestaw testowy dostępny; odbiór na telefonie do wykonania |
| Cytować istniejące zasoby i ujawnić znaczące użycie AI (s. 3) | Expo/RN, LiteRT-LM, Gemma, wygenerowany syntetyczny korpus, Codex | `RESOURCES.md` i sekcja prezentacji | Lista dostępna; zespół musi uzupełnić pozostałe użyte narzędzia |
| Odróżnić pracę istniejącą przed eventem od pracy podczas eventu (s. 3) | Historia Git i zapisany zakres szkieletu | Historia commitów, deklaracja zespołu | Datę startu eventu i deklarację potwierdza zespół |
| Zespół odpowiada za funkcjonalność, bezpieczeństwo i licencje (s. 3) | Model importowany z pliku po uzyskaniu dostępu zgodnie z licencją | Przegląd licencji i możliwość obrony architektury | Do potwierdzenia przez zespół |

## Kryteria oceny

Wagi z PDF: Idea & Innovation 30%, Relation to Category 20%, Practical Applicability / Usability 20%, Design 20%, Completeness & Implementation Value 10% (s. 1–2). Ochrona offline jest argumentem koncepcyjnym, ale oceny jakości nie należy zastępować obietnicą — potrzebne są rzeczywiste wyniki i pokazanie ograniczeń.

## Wymagania zgłoszenia

Wymagane (s. 3–4): tytuł projektu, nazwa zespołu, członkowie zespołu, opis projektu oraz prezentacja PDF mająca maksymalnie 10 slajdów. Dodatkowo można dołączyć screenshoty, repozytorium, demo i materiały graficzne. Zgłoszenie trafia do Challenge Rocket; dopuszczalny język polski lub angielski. Obecne repo nie stanowi kompletnego zgłoszenia. Zespół musi dostarczyć swoje dane i prezentację.

## Interpretacja

PDF nie wymaga lokalnego modelu, Androida, konkretnego dostawcy ani obowiązkowej chmury. Są to wybory Guardian. Ograniczenie swobodnych wyjaśnień modelu do bezpiecznych szablonów jest naszym wyborem prywatności, nie wymaganiem regulaminu. Kolejne zasady partnera challenge'u, jeśli zostaną ogłoszone, należy sprawdzić osobno.
