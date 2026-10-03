import { useLocalSearchParams } from 'expo-router';
import { Screen, Copy, Card, Action, InlineError } from '@/components/ui';
import { useGuardianResults, useAction } from '@/services/queries';
import { guardian } from '@/services/guardian';
import { riskLabels, categoryLabels, signalLabels } from '@/types/guardian';

export default function AlertDetail() {
  const { id } = useLocalSearchParams<{ id: string }>(); const results = useGuardianResults(); const action = useAction();
  const result = results.data?.find(value => value.id === id);
  if (!result) return <Screen><Copy title>{results.isPending ? 'Wczytuję analizę…' : 'Analiza niedostępna'}</Copy><Copy>Wynik mógł zostać usunięty lub wygasł po 7 dniach.</Copy></Screen>;
  return <Screen><Copy title>{riskLabels[result.risk]}</Copy><Copy>{categoryLabels[result.category]} · {result.sourceApp}</Copy>
    <Card><Copy title>Co zauważył model</Copy>{result.signals.map(signal => <Copy key={signal}>{signalLabels[signal]}</Copy>)}{result.signals.length === 0 && <Copy>Brak jednoznacznych przesłanek.</Copy>}<Copy selectable>{result.explanation}</Copy></Card>
    <Card><Copy title>Co możesz zrobić</Copy><Copy selectable>{result.recommendedAction}</Copy></Card>
    <Copy>To ocena AI, a nie potwierdzenie oszustwa. „Sprawdzone” oznacza tylko, że przejrzałeś wynik.</Copy>
    <InlineError message={action.error} />
    <Action label={result.reviewStatus === 'reviewed' ? 'Oznaczone jako sprawdzone' : 'Oznacz jako sprawdzone'} disabled={action.busy || result.reviewStatus === 'reviewed'} onPress={() => { void action.run(() => guardian.review(result.id)); }} />
  </Screen>;
}
