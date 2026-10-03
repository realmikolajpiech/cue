import type { Threat } from './store';
// Plain-language copy for the sample alerts; assessment data stays unchanged.
export function threatCopy(threat: Threat) {
  switch (threat.id) {
    case '1': return { title: 'Ktoś może udawać osobę z Twojej rodziny', warning: 'Uważaj — ktoś prosi o pieniądze', advice: 'Zadzwoń do bliskiej osoby na numer, który już znasz. Nie wysyłaj pieniędzy, dopóki nie upewnisz się, z kim rozmawiasz.' };
    case '2': return { title: 'Link do płatności może być fałszywy', warning: 'Uważaj — nie otwieraj tego linku', advice: 'Otwórz oficjalną aplikację firmy. Sprawdź tam, czy rzeczywiście musisz coś zapłacić.' };
    case '3': return { title: 'Ktoś prosi o Twój kod', warning: 'Uważaj — nie podawaj swojego kodu', advice: 'Nie wysyłaj kodów z SMS-a ani haseł. Zadzwoń do nadawcy na numer, który już znasz.' };
    default: return { title: threat.title, warning: 'Zachowaj ostrożność', advice: threat.advice };
  }
}
