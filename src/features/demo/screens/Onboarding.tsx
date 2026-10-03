import { router } from 'expo-router';
import { Screen, Copy, Card, Icon, Action } from '@/components/ui';

export default function Help() {
  return <Screen title="Jak działa aplikacja?" header={false} back>
    <Icon name="shield" size={48} />
    <Copy>Guardian ma pomóc Ci rozpoznać podejrzaną wiadomość i podpowiedzieć, co zrobić.</Copy>
    <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>1. Zobacz ostrzeżenie</Copy><Copy>Gdy wiadomość budzi wątpliwości, zobaczysz wyraźny komunikat.</Copy></Card>
    <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>2. Przeczytaj poradę</Copy><Copy>Naciśnij „Co mam zrobić?”, aby zobaczyć bezpieczny następny krok.</Copy></Card>
    <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>3. Sprawdź nadawcę</Copy><Copy>Przed wysłaniem pieniędzy lub danych zadzwoń do tej osoby na znany Ci numer.</Copy></Card>
    <Copy>{process.env.EXPO_OS === 'android' ? 'Po skonfigurowaniu modelu i uprawnień Guardian analizuje nowe powiadomienia lokalnie. AI może się pomylić.' : 'Teraz oglądasz wersję pokazową z przykładami.'}</Copy>
    <Action label="Rozumiem" onPress={() => router.canGoBack() ? router.back() : router.replace('/')} />
  </Screen>;
}
