import { t, useLanguage } from '@/i18n';
import { router, useLocalSearchParams } from 'expo-router';
import { Action, Copy, Screen } from '@/components/ui';
import { ThreatDetail } from '@/features/protection/ThreatDetail';
import { useProtection } from '@/features/protection/useProtection';

export default function Detail() { useLanguage();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { threats, review, native, busy, error, loadingResults, resultsError, refreshResults } = useProtection();
  const threat = threats.find(t => t.id === id);
  if (!threat) return <Screen header={false} back title={loadingResults ? t('Wczytuję ostrzeżenie…') : resultsError ? t('Nie można wczytać ostrzeżenia') : t('Ostrzeżenie niedostępne')}>
    {!loadingResults && <Copy>{resultsError ? t('Spróbuj ponownie.') : t('Mogło zostać usunięte lub wygasło po 7 dniach.')}</Copy>}
    {resultsError && <Action label={t("Spróbuj ponownie")} onPress={() => { void refreshResults(); }} />}
    {!loadingResults && <Action secondary label={t("Wróć do ostrzeżeń")} onPress={() => router.replace('/alerts')} />}
  </Screen>;
  return <Screen header={false} back>
    <ThreatDetail key={threat.id} threat={threat} demo={!native} busy={busy} error={error} onReview={() => { void review(threat.id); }} />
  </Screen>;
}
