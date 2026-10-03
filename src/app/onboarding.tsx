import { SymbolView } from 'expo-symbols';
import { Screen, Copy, Card, Action } from '@/components/ui';
import { usePreferences } from '@/features/preferences';
import { useTheme } from '@/theme/useTheme';

export default function Onboarding() {
  const { colors } = useTheme();
  const complete = usePreferences(s => s.completeOnboarding);
  return <Screen>
    <SymbolView name={{ ios: 'shield.lefthalf.filled', android: 'shield', web: 'shield' }} tintColor={colors.accent} size={64} />
    <Copy title>Chwila namysłu przed decyzją.</Copy>
    <Copy>Guardian szuka oznak manipulacji w kontekście powiadomień z wybranych komunikatorów i SMS.</Copy>
    <Card><Copy title>Twoje wiadomości pozostają na telefonie</Copy><Copy>Analiza działa lokalnie na Androidzie. Historia przechowuje tylko wyniki, bez wiadomości i nazw kontaktów.</Copy></Card>
    <Copy>AI może się pomylić. Sprawdzaj nietypowe prośby niezależnym kanałem. Dostęp do powiadomień włączysz samodzielnie po konfiguracji.</Copy>
    <Action label="Przejdź do konfiguracji" onPress={complete} />
  </Screen>;
}
