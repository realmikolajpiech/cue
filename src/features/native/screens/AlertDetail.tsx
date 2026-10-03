import { t, useLanguage } from '@/i18n';
import { useLocalSearchParams } from 'expo-router';
import { Screen, Copy, Action } from '@/components/ui';
import { ThreatDetail } from '@/features/protection/ThreatDetail';
import { toThreat } from '@/features/protection/presentation';
import { useGuardianResults, useAction } from '@/services/queries';
import { guardian } from '@/services/guardian';

export default function AlertDetail() {
  useLanguage();
  const { id } = useLocalSearchParams<{ id: string }>();
  const results = useGuardianResults();
  const action = useAction();
  const result = results.data?.find(value => value.id === id);
  if (!result) return <Screen title={results.isPending ? t('Wczytuję analizę…') : results.isError ? t('Nie można wczytać analizy') : t('Analiza niedostępna')}>
    {!results.isPending && <Copy>{results.isError ? t('Spróbuj ponownie.') : t('Wynik mógł zostać usunięty lub wygasł po 7 dniach.')}</Copy>}
    {results.isError && <Action label={t("Spróbuj ponownie")} onPress={() => { void results.refetch(); }} />}
  </Screen>;
  return <Screen header={false}>
    <ThreatDetail key={result.id} threat={toThreat(result)} busy={action.busy} error={action.error} onReview={() => { void action.run(() => guardian.review(result.id)); }} />
  </Screen>;
}
