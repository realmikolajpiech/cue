import { useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, TextInput } from 'react-native';
import { router } from 'expo-router';
import { Screen, Copy, Card, Row, Action, InlineError, Risk } from '@/components/ui';
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
  return <Screen>
    <TextInput accessibilityLabel="Wiadomość lub link do sprawdzenia" placeholder="Tutaj wklej wiadomość…" placeholderTextColor={colors.secondaryText}
      value={message} editable={!busy} maxLength={1500} onChangeText={text => { setMessage(text); setResult(null); setError(null); }} multiline textAlignVertical="top"
      style={{ minHeight: 200, padding: 20, borderWidth: 2, borderColor: colors.border, borderRadius: 16, backgroundColor: colors.surface, color: colors.text, fontFamily: 'DMSans', fontSize: 22, lineHeight: 33 }} />
    <Action label={busy ? 'Sprawdzam wiadomość…' : 'Sprawdź wiadomość'} disabled={busy || !message.trim() || !status?.available || !status.modelInstalled} onPress={() => { void check(); }} />
    {busy && <Row><ActivityIndicator color={colors.text} /><Copy accessibilityLiveRegion="polite">Analizuję wiadomość na telefonie…</Copy></Row>}
    <InlineError message={error} />
    {status && !status.available && <Copy>Analiza wymaga buildu Guardian na Androidzie.</Copy>}
    {status?.available && !status.modelInstalled && <Action secondary label="Skonfiguruj model" onPress={() => router.push('/model-setup')} />}
    {result && <Card>
      <Risk label={riskLabels[result.risk]} />
      <Copy title style={{ fontSize: 26, lineHeight: 36 }}>{categoryLabels[result.category]}</Copy>
      <Copy>{result.explanation}</Copy>
      <Copy title>Co zrobić teraz?</Copy><Copy>{result.recommendedAction}</Copy>
      <Copy>Analiza lokalna. AI może się pomylić.</Copy>
      <Action label="Wpisz inną wiadomość" secondary onPress={() => { setMessage(''); setResult(null); setError(null); }} />
    </Card>}
  </Screen>;
}
