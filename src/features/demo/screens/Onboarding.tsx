import { t, useLanguage } from '@/i18n/legacy';
import { router } from 'expo-router';
import { Screen, Copy, Card, Icon, Action } from '@/components/ui';

export default function Help() { useLanguage();
  return <Screen title={t("Jak działa aplikacja?")} header={false} back>
    <Icon name="shield" size={48} />
    <Copy>{t("Guardian ma pomóc Ci rozpoznać podejrzaną wiadomość i podpowiedzieć, co zrobić.")}</Copy>
    <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>{t("1. Zobacz ostrzeżenie")}</Copy><Copy>{t("Gdy wiadomość budzi wątpliwości, zobaczysz wyraźny komunikat.")}</Copy></Card>
    <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>{t("2. Przeczytaj poradę")}</Copy><Copy>{t("Naciśnij „Co mam zrobić?”, aby zobaczyć bezpieczny następny krok.")}</Copy></Card>
    <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>{t("3. Sprawdź nadawcę")}</Copy><Copy>{t("Przed wysłaniem pieniędzy lub danych zadzwoń do tej osoby na znany Ci numer.")}</Copy></Card>
    <Copy>{process.env.EXPO_OS === 'android' ? t("Po skonfigurowaniu modelu i uprawnień Guardian analizuje nowe powiadomienia lokalnie. AI może się pomylić.") : t("Teraz oglądasz wersję pokazową z przykładami.")}</Copy>
    <Action label={t("Rozumiem")} onPress={() => router.canGoBack() ? router.back() : router.replace('/')} />
  </Screen>;
}
