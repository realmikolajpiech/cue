import { t } from '@/i18n/legacy';
import type { Threat } from './store';
// Plain-language copy for the sample alerts; assessment data stays unchanged.
export function threatCopy(threat: Threat) {
  switch (threat.id) {
    case '1': return { goal: t("Nakłonić Cię do wysłania pieniędzy, udając bliską osobę."), cautions: [t("Nowy numer telefonu nie potwierdza, kto pisze."), t("Pośpiech ma sprawić, że wyślesz pieniądze bez sprawdzenia."), t("Nie wysyłaj przelewu ani kodu BLIK.")],  title: t("Ktoś może udawać osobę z Twojej rodziny"), warning: t("Uważaj — ktoś prosi o pieniądze"), advice: t("Zadzwoń do bliskiej osoby na numer, który już znasz. Nie wysyłaj pieniędzy, dopóki nie upewnisz się, z kim rozmawiasz.") };
    case '2': return { goal: t("Skłonić Cię do wejścia na fałszywą stronę i podania danych do płatności."), cautions: [t("Link może prowadzić do strony udającej znaną firmę."), t("Nawet mała dopłata może być pretekstem do zdobycia danych."), t("Nie wpisuj danych karty ani hasła do banku.")],  title: t("Link do płatności może być fałszywy"), warning: t("Uważaj — nie otwieraj tego linku"), advice: t("Otwórz oficjalną aplikację firmy. Sprawdź tam, czy rzeczywiście musisz coś zapłacić.") };
    case '3': return { goal: t("Zdobyć kod, który pozwoli zalogować się na Twoje konto lub potwierdzić płatność."), cautions: [t("Kod z SMS-a jest przeznaczony tylko dla Ciebie."), t("Nadawca może udawać znajomego lub pracownika firmy."), t("Nie podawaj kodu, nawet gdy ktoś bardzo się spieszy.")],  title: t("Ktoś prosi o Twój kod"), warning: t("Uważaj — nie podawaj swojego kodu"), advice: t("Nie wysyłaj kodów z SMS-a ani haseł. Zadzwoń do nadawcy na numer, który już znasz.") };
    default: return { goal: t("Nakłonić Cię do działania, zanim sprawdzisz nadawcę."), cautions: [t("Nie wysyłaj pieniędzy ani danych pod presją."), t("Potwierdź prośbę, kontaktując się z nadawcą na znany Ci numer.")],  title: threat.title, warning: t("Zachowaj ostrożność"), advice: threat.advice };
  }
}
