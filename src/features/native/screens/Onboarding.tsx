import { t, useLanguage } from '@/i18n/legacy';
import { SymbolView } from 'expo-symbols';
import { Screen, Copy, Card, Action } from '@/components/ui';
import { usePreferences } from '@/features/preferences';
import { useTheme } from '@/theme/useTheme';

export default function Onboarding() { useLanguage();
  const { colors } = useTheme();
  const complete = usePreferences(s => s.completeOnboarding);
  return <Screen>
    <SymbolView name={{ ios: 'shield.lefthalf.filled', android: 'shield', web: 'shield' }} tintColor={colors.accent} size={64} />
    <Copy title>{t("Chwila namysłu przed decyzją.")}</Copy>
    <Copy>{t("Guardian szuka oznak manipulacji w kontekście powiadomień z wybranych komunikatorów i SMS.")}</Copy>
    <Card><Copy title>{t("Twoje wiadomości pozostają na telefonie")}</Copy><Copy>{t("Analiza działa lokalnie na Androidzie. Przy ostrzeżeniu zapisujemy nadawcę i krótki fragment powiadomienia na 7 dni.")}</Copy></Card>
    <Copy>{t("AI może się pomylić. Sprawdzaj nietypowe prośby niezależnym kanałem. Dostęp do powiadomień włączysz samodzielnie po konfiguracji.")}</Copy>
    <Action label={t("Przejdź do konfiguracji")} onPress={complete} />
  </Screen>;
}
