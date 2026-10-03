import { t, useLanguage } from '@/i18n';
import { useLocalSearchParams } from 'expo-router';
import { Screen, Copy, Card, Action, InlineError } from '@/components/ui';
import { useGuardianResults, useAction } from '@/services/queries';
import { guardian } from '@/services/guardian';
import { riskLabels, categoryLabels, signalLabels } from '@/types/guardian';

export default function AlertDetail() { useLanguage();
  const { id } = useLocalSearchParams<{ id: string }>(); const results = useGuardianResults(); const action = useAction();
  const result = results.data?.find(value => value.id === id);
  if (!result) return <Screen><Copy title>{results.isPending ? t("Wczytuję analizę…") : t("Analiza niedostępna")}</Copy><Copy>{t("Wynik mógł zostać usunięty lub wygasł po 7 dniach.")}</Copy></Screen>;
  return <Screen><Copy title>{t(riskLabels[result.risk])}</Copy><Copy>{t(categoryLabels[result.category])} · {result.sourceApp}</Copy>
    <Card><Copy title>{t("Co zauważył model")}</Copy>{result.signals.map(signal => <Copy key={signal}>{t(signalLabels[signal])}</Copy>)}{result.signals.length === 0 && <Copy>{t("Brak jednoznacznych przesłanek.")}</Copy>}<Copy selectable>{result.explanation}</Copy></Card>
    <Card><Copy title>{t("Co możesz zrobić")}</Copy><Copy selectable>{result.recommendedAction}</Copy></Card>
    <Copy>{t("To ocena AI, a nie potwierdzenie oszustwa. „Sprawdzone” oznacza tylko, że przejrzałeś wynik.")}</Copy>
    <InlineError message={action.error} />
    <Action label={result.reviewStatus === 'reviewed' ? t("Oznaczone jako sprawdzone") : t("Oznacz jako sprawdzone")} disabled={action.busy || result.reviewStatus === 'reviewed'} onPress={() => { void action.run(() => guardian.review(result.id)); }} />
  </Screen>;
}
