import { useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, TextInput } from 'react-native';
import { router } from 'expo-router';
import { Screen, Copy, Card, Row, Icon, SectionHeading, Action, InlineError, Risk } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { guardian } from '@/services/guardian';
import { useGuardianStatus } from '@/services/queries';
import { categoryLabels, riskLabels, type ManualResult } from '@/types/guardian';

export default function CheckMessage() {
  const [message, setMessage] = useState('');
  const [result, setResult] = useState<ManualResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pending = useRef(false);
  const { colors } = useTheme();
  const { data: status } = useGuardianStatus();
  async function check() {
    if (pending.current || !message.trim()) return;
    pending.current = true;
    setBusy(true); setError(null); setResult(null);
    Keyboard.dismiss();
    try { setResult(await guardian.checkMessage(message.trim())); }
    catch (cause) {
      const code = cause instanceof Error ? cause.message : '';
      setError(code.includes('model_not_ready') ? 'Model nie jest gotowy. Otwórz konfigurację Gemma i zaimportuj model.'
        : code.includes('benchmark_already_running') ? 'Trwa benchmark. Zaczekaj na jego zakończenie.'
        : code.includes('checkMessage') && code.includes('function') ? 'Ta wersja aplikacji wymaga aktualizacji buildu Android.'
        : 'Nie udało się sprawdzić wiadomości. Spróbuj ponownie lub sprawdź konfigurację modelu.');
    } finally { pending.current = false; setBusy(false); }
  }
  return <Screen title="Sprawdź wiadomość" risk={result?.risk}>
    {result ? <Card>
      <Risk label={riskLabels[result.risk]} />
      <Copy title style={{ fontSize: 20 }}>{categoryLabels[result.category]}</Copy>
      <Copy>{result.explanation}</Copy>
      <SectionHeading title="Co możesz zrobić" />
      <Copy>{result.recommendedAction}</Copy>
      <Copy style={{ fontSize: 10 }}>Analiza lokalna · AI może się pomylić. Brak ostrzeżenia nie gwarantuje bezpieczeństwa.</Copy>
      <Action label="Sprawdź kolejną wiadomość" onPress={() => { setMessage(''); setResult(null); setError(null); }} />
    </Card> : <Card>
      <SectionHeading title="Wiadomość lub link"><Icon name="scan" /></SectionHeading>
      <TextInput accessibilityLabel="Treść wiadomości lub link" placeholder="Wklej tutaj treść, która budzi Twoje wątpliwości…" placeholderTextColor={colors.secondaryText} value={message} editable={!busy} maxLength={1500} onChangeText={text => { setMessage(text); setError(null); }} multiline textAlignVertical="top" style={{ minHeight: 220, padding: 18, borderWidth: 1, borderColor: colors.border, borderRadius: 15, backgroundColor: colors.secondary, color: colors.text, fontFamily: 'DMSans', fontSize: 14, lineHeight: 25 }} />
      <Row style={{ gap: 6 }}><Icon name="lock" size={14} /><Copy style={{ fontSize: 10 }}>Treść i wynik nie są zapisywane · maks. 1500 znaków</Copy></Row>
      <Action label={busy ? 'Analizuję wiadomość…' : 'Sprawdź'} icon="arrow" disabled={busy || !message.trim() || !status?.available || !status.modelInstalled} onPress={() => { void check(); }} />
      {busy && <Row><ActivityIndicator color={colors.text} /><Copy accessibilityLiveRegion="polite">Gemma analizuje lokalnie. Weryfikacja podejrzenia może potrwać dłużej.</Copy></Row>}
      <InlineError message={error} />
      {status && !status.available && <Copy>Analiza wymaga własnego buildu Guardian na Androidzie.</Copy>}
      {status?.available && !status.modelInstalled && <><Copy>Najpierw zainstaluj lokalny model Gemma.</Copy><Action secondary label="Skonfiguruj model" onPress={() => router.push('/model-setup')} /></>}
      {status?.modelInstalled && !busy && <Copy style={{ fontSize: 10 }}>Gemma sprawdzi tekst na telefonie. Dostęp do powiadomień nie jest wymagany.</Copy>}
    </Card>}
  </Screen>;
}
