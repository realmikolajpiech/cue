import { LanguagePicker } from '@/components/LanguagePicker';
import { t, useLanguage } from '@/i18n';
import { useState } from 'react';
import { Alert } from 'react-native';
import { Screen, Card, Copy, Action, InlineError } from '@/components/ui';
import { useGuardianStatus, useAction } from '@/services/queries';
import { guardian } from '@/services/guardian';

export default function Settings() { useLanguage();
  const { data: s } = useGuardianStatus(); const action = useAction();
  const [report, setReport] = useState<Record<string, number | null> | null>(null);
  return <Screen><Copy title>{t("Prywatność pod Twoją kontrolą")}</Copy>
    <LanguagePicker /><Card><Copy title>{t("Maximum Privacy")}</Copy><Copy>{t("Analiza jest lokalna. Guardian nie wysyła wiadomości do chmury. Kontekst jest przechowywany tylko w RAM, do 5 wiadomości przez maksymalnie 15 minut.")}</Copy><Copy>{t('Historia wyboru zawiera do 100 powiadomień tylko w RAM, przez maksymalnie 15 minut. Wyłączenie ochrony usuwa tę historię.')}</Copy></Card>
    <Card><Copy title>{t("Silnik")}</Copy><Copy>LiteRT-LM {s?.runtimeVersion ?? '0.15.0'} · {s?.backend ?? 'none'}</Copy><Copy>{t("Prompt:")} {s?.promptVersion ?? 'guardian-pl-v3-evidence'}</Copy>{s?.modelSha256 && <Copy selectable>{t("SHA-256 modelu:")} {s.modelSha256}</Copy>}<Copy>{t("Mały model może przeoczyć oszustwo lub błędnie ocenić poprawną prośbę. Sprawdzaj informacje niezależnym kanałem.")}</Copy></Card>
    <Card><Copy title>{t("Benchmark lokalnego modelu")}</Copy><Copy>{t("40 syntetycznych rozmów po polsku: 20 oszustw i 20 poprawnych sytuacji. Test wstrzymuje monitorowanie; wynik nie trafia do historii prywatnych analiz.")}</Copy>
      <Action label={s?.benchmarkRunning ? t('Test {{progress}}/40…', { progress: s.benchmarkProgress }) : t("Uruchom benchmark")} disabled={action.busy || !s?.available || !s.modelInstalled} onPress={() => { void action.run(async () => { setReport((await guardian.benchmark()).metrics); }); }} />
      {s?.benchmarkRunning && <Action label={t("Anuluj benchmark")} secondary onPress={guardian.cancelBenchmark} />}
      {report && <Copy selectable>{Object.entries(report).map(([key, value]) => `${key}: ${value === null ? t("brak danych") : Math.round(value * 1000) / 1000}`).join('\n')}</Copy>}
    </Card>
    <InlineError message={action.error} />
    <Action label={t("Usuń historię analiz")} secondary disabled={!s?.available || action.busy} onPress={() => Alert.alert(t("Usunąć historię?"), t("Wszystkie lokalne wyniki zostaną usunięte. Bieżąca analiza zostanie anulowana."), [{ text: t("Anuluj"), style: 'cancel' }, { text: t("Usuń"), style: 'destructive', onPress: () => { void action.run(guardian.clear); } }])} />
  </Screen>;
}
