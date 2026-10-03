import type { Threat } from './store';
// Plain-language copy for the sample alerts; assessment data stays unchanged.
export function threatCopy(threat: Threat) {
  switch (threat.id) {
    case '1': return { goal: 'Nakłonić Cię do wysłania pieniędzy, udając bliską osobę.', cautions: ['Nowy numer telefonu nie potwierdza, kto pisze.', 'Pośpiech ma sprawić, że wyślesz pieniądze bez sprawdzenia.', 'Nie wysyłaj przelewu ani kodu BLIK.'],  title: 'Ktoś może udawać osobę z Twojej rodziny', warning: 'Uważaj — ktoś prosi o pieniądze', advice: 'Zadzwoń do bliskiej osoby na numer, który już znasz. Nie wysyłaj pieniędzy, dopóki nie upewnisz się, z kim rozmawiasz.' };
    case '2': return { goal: 'Skłonić Cię do wejścia na fałszywą stronę i podania danych do płatności.', cautions: ['Link może prowadzić do strony udającej znaną firmę.', 'Nawet mała dopłata może być pretekstem do zdobycia danych.', 'Nie wpisuj danych karty ani hasła do banku.'],  title: 'Link do płatności może być fałszywy', warning: 'Uważaj — nie otwieraj tego linku', advice: 'Otwórz oficjalną aplikację firmy. Sprawdź tam, czy rzeczywiście musisz coś zapłacić.' };
    case '3': return { goal: 'Zdobyć kod, który pozwoli zalogować się na Twoje konto lub potwierdzić płatność.', cautions: ['Kod z SMS-a jest przeznaczony tylko dla Ciebie.', 'Nadawca może udawać znajomego lub pracownika firmy.', 'Nie podawaj kodu, nawet gdy ktoś bardzo się spieszy.'],  title: 'Ktoś prosi o Twój kod', warning: 'Uważaj — nie podawaj swojego kodu', advice: 'Nie wysyłaj kodów z SMS-a ani haseł. Zadzwoń do nadawcy na numer, który już znasz.' };
    default: return { goal: 'Nakłonić Cię do działania, zanim sprawdzisz nadawcę.', cautions: ['Nie wysyłaj pieniędzy ani danych pod presją.', 'Potwierdź prośbę, kontaktując się z nadawcą na znany Ci numer.'],  title: threat.title, warning: 'Zachowaj ostrożność', advice: threat.advice };
  }
}
