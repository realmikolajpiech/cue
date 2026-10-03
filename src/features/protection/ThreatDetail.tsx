import { t, useLanguage } from '@/i18n';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Action, Card, Copy, Icon, InlineError, Row } from '@/components/ui';
import type { Threat } from '@/features/demo/store';
import { isWarning } from './presentation';
import { useTheme } from '@/theme/useTheme';

type Props = { threat: Threat; busy: boolean; error: string | null; onReview: () => void; demo?: boolean; showReviewAction?: boolean };

export function ThreatDetail({ threat, busy, error, onReview, demo = false, showReviewAction = true }: Props) {
  useLanguage();
  const { colors } = useTheme();
  const [expanded, setExpanded] = useState(false);
  const warning = isWarning(threat);
  const context = threat.notificationContext;
  const latest = context?.messages.at(-1);
  const earlier = context?.messages.slice(0, -1) ?? [];
  const sender = latest?.sender || context?.title;
  return <>
    <View style={{ gap: 12 }}>
      <Row style={{ gap: 8 }}><Icon name={warning ? 'warning' : 'shield'} size={20} color={warning ? colors.warning : colors.secondaryText} /><Copy style={[styles.label, { color: warning ? colors.warning : colors.secondaryText }]}>{t(threat.risk)}</Copy></Row>
      <Copy title accessibilityRole="header" style={styles.title}>{t(threat.title)}</Copy>
    </View>
    <Card style={styles.card}>
      <Row style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
        <Row style={{ gap: 8 }}><Icon name="message" size={20} /><Copy style={[styles.label, { color: colors.text }]}>{threat.source}</Copy></Row>
        <Copy style={[styles.date, { fontVariant: ['tabular-nums'] }]}>{threat.time}</Copy>
      </Row>
      {demo && <Copy style={styles.date}>Przykładowe powiadomienie</Copy>}
      {context?.title && context.title !== sender && <Copy selectable style={styles.label}>{context.title}</Copy>}
      {sender ? <Copy selectable style={[styles.sender, { color: colors.text }]}>{sender}</Copy> : null}
      {latest ? <Copy selectable style={[styles.message, { color: colors.text }]}>{latest.text}</Copy> : <Copy style={styles.body}>Treść tego powiadomienia nie została zachowana.</Copy>}
      {earlier.length > 0 && <>
        <Pressable accessibilityRole="button" accessibilityState={{ expanded }} onPress={() => setExpanded(value => !value)} style={{ minHeight: 44, justifyContent: 'center' }}>
          <Copy style={[styles.label, { color: colors.text }]}>{expanded ? 'Ukryj wcześniejsze wiadomości' : `Wcześniejsze wiadomości w analizie (${earlier.length})`}</Copy>
        </Pressable>
        {expanded && earlier.map((message, index) => <View key={index} style={[styles.previous, { borderTopColor: colors.border }]}>
          {message.sender ? <Copy selectable style={[styles.label, { color: colors.text }]}>{message.sender}</Copy> : null}
          <Copy selectable style={styles.body}>{message.text}</Copy>
        </View>)}
      </>}
    </Card>
    {warning && threat.signals.length > 0 && <View style={{ gap: 12 }}>
      <Copy style={[styles.sectionTitle, { color: colors.text }]}>Co wykryto</Copy>
      <Row style={{ flexWrap: 'wrap', gap: 8 }}>{threat.signals.map(signal => <Copy key={signal} style={[styles.signal, { color: colors.warning, backgroundColor: colors.warningSoft }]}>{t(signal)}</Copy>)}</Row>
    </View>}
    {threat.risk !== 'Niskie ryzyko' && <Card style={[styles.card, { backgroundColor: warning ? colors.warningSoft : colors.surface }]}>
      <Copy style={[styles.sectionTitle, { color: colors.text }]}>Co zrobić teraz</Copy>
      <Copy selectable style={[styles.body, { color: colors.text }]}>{t(threat.advice)}</Copy>
    </Card>}
    <InlineError message={error} />
    {showReviewAction && <Action label={t(threat.reviewed ? 'Przeczytane' : busy ? 'Zapisuję…' : 'Oznacz jako przeczytane')} icon="check" secondary={threat.reviewed} disabled={busy || threat.reviewed} onPress={onReview} />}
  </>;
}

const styles = StyleSheet.create({
  title: { fontSize: 28, lineHeight: 36, letterSpacing: -0.6 }, label: { fontSize: 16, lineHeight: 24, fontFamily: 'DMSansMedium' },
  card: { padding: 20, gap: 12, borderCurve: 'continuous' }, date: { fontSize: 14, lineHeight: 22 },
  sender: { fontSize: 20, lineHeight: 28, fontFamily: 'DMSansSemiBold' }, message: { fontSize: 19, lineHeight: 28 },
  body: { fontSize: 18, lineHeight: 27 }, sectionTitle: { fontSize: 18, lineHeight: 26, fontFamily: 'DMSansSemiBold' },
  signal: { fontSize: 15, lineHeight: 22, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 12, borderCurve: 'continuous' },
  previous: { borderTopWidth: 1, paddingTop: 12, gap: 8 },
});
