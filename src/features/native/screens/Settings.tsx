import { useState } from 'react';
import { Alert } from 'react-native';
import { Screen, Card, Copy, Action, InlineError } from '@/components/ui';
import { useGuardianStatus, useAction } from '@/services/queries';
import { guardian } from '@/services/guardian';

export default function Settings() {
  const { data: s } = useGuardianStatus(); const action = useAction();
  const [report, setReport] = useState<Record<string, number | null> | null>(null);
  return <Screen><Copy title>Prywatność pod Twoją kontrolą</Copy>
    <Card><Copy title>Maximum Privacy</Copy><Copy>Analiza jest lokalna. Guardian nie wysyła wiadomości do chmury. Kontekst jest przechowywany tylko w RAM, do 5 wiadomości przez maksymalnie 15 minut.</Copy></Card>
    <Card><Copy title>Silnik</Copy><Copy>LiteRT-LM {s?.runtimeVersion ?? '0.15.0'} · {s?.backend ?? 'none'}</Copy><Copy>Prompt: {s?.promptVersion ?? 'guardian-pl-v2'}</Copy>{s?.modelSha256 && <Copy selectable>SHA-256 modelu: {s.modelSha256}</Copy>}<Copy>Mały model może przeoczyć oszustwo lub błędnie ocenić poprawną prośbę. Sprawdzaj informacje niezależnym kanałem.</Copy></Card>
    <Card><Copy title>Benchmark lokalnego modelu</Copy><Copy>40 syntetycznych rozmów po polsku: 20 oszustw i 20 poprawnych sytuacji. Test wstrzymuje monitorowanie; wynik nie trafia do historii prywatnych analiz.</Copy>
      <Action label={s?.benchmarkRunning ? `Test ${s.benchmarkProgress}/40…` : 'Uruchom benchmark'} disabled={action.busy || !s?.available || !s.modelInstalled} onPress={() => { void action.run(async () => { setReport((await guardian.benchmark()).metrics); }); }} />
      {s?.benchmarkRunning && <Action label="Anuluj benchmark" secondary onPress={guardian.cancelBenchmark} />}
      {report && <Copy selectable>{Object.entries(report).map(([key, value]) => `${key}: ${value === null ? 'brak danych' : Math.round(value * 1000) / 1000}`).join('\n')}</Copy>}
    </Card>
    <InlineError message={action.error} />
    <Action label="Usuń historię analiz" secondary disabled={!s?.available || action.busy} onPress={() => Alert.alert('Usunąć historię?', 'Wszystkie lokalne wyniki zostaną usunięte. Bieżąca analiza zostanie anulowana.', [{ text: 'Anuluj', style: 'cancel' }, { text: 'Usuń', style: 'destructive', onPress: () => { void action.run(guardian.clear); } }])} />
  </Screen>;
}
