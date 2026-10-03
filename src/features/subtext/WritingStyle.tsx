import { View, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, Copy, Icon, Row } from '@/components/ui';
import { subtext, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, Page, ui } from './components';

export default function WritingStyle({ roomId, personName }: { roomId?: string; personName?: string }) {
  const { colors } = useTheme();
  const client = useQueryClient();
  const { data: status } = useSubtextStatus();
  const queryKey = ['subtext', 'writing-style', roomId ?? 'general'];
  const preview = useMutation({ mutationFn: () => roomId ? subtext.previewConversationWritingStyle(roomId) : subtext.previewWritingStyle(), onSuccess: result => {
    client.setQueryData(queryKey, result);
  } });
  const query = useQuery({ queryKey, queryFn: () => roomId ? subtext.conversationWritingStyle(roomId) : subtext.writingStyle() });
  const style = query.data;
  const hasMessages = !!style?.sampleCount;
  const examples = style?.examples ?? [];

  return <Page>
    <View style={{ gap: 10 }}>
      <Copy style={ui.eyebrow}>Tak piszesz Ty</Copy>
      <Copy title style={ui.heading}>{roomId ? 'Pamięć rozmowy' : 'Twój styl.'}</Copy>
      <Copy style={ui.body}>{roomId ? `Tak piszesz do: ${personName}. Pamięć uzupełnia się po synchronizacji nowych wiadomości i pozostaje osobna dla tego czatu.` : 'Twój sposób pisania, zebrany z różnych rozmów. Zobacz, jak brzmi na co dzień.'}</Copy>
    </View>
    <ErrorText error={query.error ?? preview.error} />
    {query.isPending ? <Row style={{ paddingVertical: 24 }}><ActivityIndicator color={colors.text} /><Copy style={ui.body}>Poznaję Twój styl…</Copy></Row> : style && <>
      {hasMessages ? <Card style={styles.summary}>
        <Row style={{ justifyContent: 'space-between' }}>
          <Row><View style={[styles.avatar, { backgroundColor: colors.secondary }]}><Icon name="style" size={22} /></View><Copy style={styles.cardTitle}>Po Twojemu</Copy></Row>
          <Pressable accessibilityRole="button" accessibilityLabel="Odśwież Twój styl" disabled={query.isFetching}
            onPress={() => { void query.refetch(); }} style={({ pressed }) => ({ minHeight: 44, minWidth: 44, alignItems: 'center', justifyContent: 'center', opacity: pressed ? 0.5 : 1 })}>
            {query.isFetching ? <ActivityIndicator color={colors.text} /> : <Icon name="history" size={20} />}
          </Pressable>
        </Row>
        <Copy style={[ui.body, { color: colors.text }]}>{style.summary || (style.habits.length
          ? 'Te cechy powtarzają się w Twoich wiadomościach.'
          : 'Każda wiadomość dodaje trochę Twojego stylu. W podglądzie znajdziesz swoje autentyczne odpowiedzi.')}</Copy>
        {!!style.habits.length && <View style={styles.tags}>{style.habits.map(habit =>
          <View key={habit} style={[styles.tag, { backgroundColor: colors.secondary }]}><Copy style={[ui.small, { color: colors.text }]}>{habit}</Copy></View>)}</View>}
        {roomId && !!style.traits?.length && <View style={{ gap: 6 }}>{style.traits.map(trait =>
          <Copy key={trait.text} style={ui.small}>{trait.text} · {trait.matches} z {trait.sampleSize} ostatnich próbek</Copy>)}</View>}
        <Copy style={ui.small}>{style.sampleCount} próbek Twoich wiadomości · {style.conversationCount} {style.conversationCount === 1 ? 'rozmowa' : 'rozmowy'}</Copy>
        {roomId && <Copy style={ui.small}>{style.sampleCount < 5 ? 'Za mało próbek, aby pewnie określić styl.' : 'Cechy opierają się na maksymalnie 60 ostatnich próbkach. Nowe wiadomości stopniowo zmieniają pamięć.'}{!!style.updatedAt && ` Ostatnia aktualizacja: ${new Date(style.updatedAt).toLocaleString('pl-PL')}.`}</Copy>}
        {status?.cloudEnabled ? <>
          <Button label={preview.isPending ? 'Przygotowuję 5 rozmów…' : style.generated || style.previewExamples?.length ? 'Odśwież przykłady AI' : 'Zobacz 5 przykładowych rozmów'}
            disabled={style.sampleCount < 5 || !!status.analyzing || preview.isPending} onPress={() => preview.mutate()} />
          <Copy style={ui.small}>{style.sampleCount < 5 ? 'Potrzebujemy przynajmniej 5 Twoich wiadomości.' : roomId ? 'AI użyje tylko Twoich wiadomości z tą osobą.' : 'AI użyje Twoich wiadomości z różnych rozmów. Próbki stylu trafią przez Supabase do DeepSeek.'}</Copy>
        </> : <Button label="Włącz analizę AI" secondary onPress={() => router.navigate('/alerts')} />}
      </Card> : <Card style={styles.summary}>
        <Icon name="style" size={28} />
        <Copy title style={ui.title}>Zacznijmy od Twoich wiadomości</Copy>
        <Copy style={ui.body}>{roomId ? 'Brakuje Twoich wiadomości w tej rozmowie. Zsynchronizuj historię, aby zobaczyć swój styl z tą osobą.' : 'Połącz Messenger lub WhatsApp i zsynchronizuj rozmowy. Twój styl powstaje z wiadomości, które piszesz Ty.'}</Copy>
        <Button label="Połącz konto" onPress={() => router.push('/connections')} />
      </Card>}

      {roomId && <>
        <Card style={styles.summary}>
          <Copy title style={ui.title}>Częste zwroty i odpowiedzi</Copy>
          {style.phrases?.length ? style.phrases.map(phrase => <Row key={phrase.text} style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Copy selectable style={[ui.body, { flex: 1 }]}>{phrase.text}</Copy><Copy style={ui.small}>{phrase.count}×</Copy>
          </Row>) : <Copy style={ui.small}>Zwroty pojawią się po co najmniej trzech użyciach w różnych Twoich wiadomościach.</Copy>}
        </Card>
        <Card style={styles.summary}>
          <Copy title style={ui.title}>Co pamiętamy o tej rozmowie</Copy>
          {style.relationship?.length ? style.relationship.map(item => <View key={item.id} style={{ gap: 4 }}>
            <Copy selectable style={ui.body}>{item.text}</Copy>
            <Copy style={ui.small}>Aktualizacja: {new Date(item.updatedAt).toLocaleDateString('pl-PL')} · {item.evidenceIds.length} {item.evidenceIds.length === 1 ? 'wiadomość jako dowód' : 'wiadomości jako dowód'}</Copy>
          </View>) : <Copy style={ui.small}>Tutaj pojawią się potwierdzone preferencje, tematy i ustalenia. Pamięć AI uzupełnia się w partiach po włączeniu analizy AI oraz przy analizowaniu rozmowy.</Copy>}
          <Copy style={ui.small}>{status?.analyzing === roomId ? 'Uzupełniam kontekst…' : !status?.cloudEnabled ? 'Aktualizacja kontekstu AI jest wyłączona. Styl nadal uzupełnia się na telefonie.' : style.pendingMessages ? `${style.pendingMessages} nowych wiadomości od ostatniej aktualizacji kontekstu. AI aktualizuje pamięć w partiach, a przy odpowiedzi czyta ostatnie 80 wiadomości.` : style.contextUpdatedAt ? `Kontekst zaktualizowany: ${new Date(style.contextUpdatedAt).toLocaleString('pl-PL')}. Przy odpowiedzi AI czyta też ostatnie 80 wiadomości.` : 'Czekamy na wystarczającą liczbę wiadomości.'}</Copy>
          {!!style.contextError && <Copy style={ui.small}>{style.contextError} Zapisana pamięć pozostaje dostępna; aktualizację ponowimy.</Copy>}
        </Card>
      </>}

      <View style={{ gap: 6 }}>
        <Copy title style={ui.title}>Tak brzmi to w rozmowie</Copy>
        <Copy style={ui.small}>{style.generated ? 'Pięć codziennych sytuacji. Odpowiedzi AI w Twoim stylu.' : examples.length ? 'Twoje autentyczne odpowiedzi. Zachowujemy też dłuższe przykłady, jeśli są w historii.' : 'Tutaj pojawi się do pięciu Twoich autentycznych odpowiedzi.'}</Copy>
      </View>
      <View style={[styles.chat, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        <Row style={[styles.chatHeader, { borderBottomColor: colors.border }]}>
          <View style={[styles.avatar, { backgroundColor: colors.secondary }]}><Icon name="message" size={20} /></View>
          <View style={{ flex: 1, gap: 2 }}><Copy style={styles.cardTitle}>Twój styl w rozmowie</Copy><Copy style={ui.small}>Podgląd · tylko dla Ciebie</Copy></View>
        </Row>
        <View style={styles.chatBody}>
          {examples.length ? examples.map((example, index) => <View key={example.id} style={styles.exchange}>
            <Copy style={[ui.small, styles.divider]}>ROZMOWA {index + 1}</Copy>
            {!!example.incoming && <View style={[styles.bubble, styles.incoming, { backgroundColor: colors.secondary }]}>
              <Copy selectable style={[styles.message, { color: colors.text }]}>{example.incoming}</Copy>
            </View>}
            <View style={[styles.bubble, styles.outgoing, { backgroundColor: colors.text }]}>
              <Copy selectable style={[styles.message, { color: colors.surface }]}>{example.reply}</Copy>
            </View>
            <Row style={{ alignSelf: 'flex-end', gap: 4 }}><Copy style={ui.small}>Ty</Copy><Icon name="check" size={13} color={colors.secondaryText} /></Row>
          </View>) : <View style={styles.emptyChat}>
            <Icon name="message" size={30} color={colors.secondaryText} />
            <Copy style={[ui.body, { textAlign: 'center' }]}>Jeszcze nie mamy krótkich wymian do pokazania.</Copy>
            <Copy style={[ui.small, { textAlign: 'center' }]}>Po synchronizacji szukamy wiadomości rozmówcy i Twojej odpowiedzi. Przykładowe rozmowy Cue nie wpływają na Twój styl.</Copy>
          </View>}
        </View>
      </View>
      {!!style.previewExamples?.length && <Card style={styles.summary}>
        <Copy title style={ui.title}>Przykładowe odpowiedzi AI</Copy>
        <Copy style={ui.small}>Wygenerowane sytuacje służą do podglądu. Nie uczymy na nich Twojej pamięci.</Copy>
        {style.previewExamples.map(example => <View key={example.id} style={{ gap: 6 }}>
          <Copy style={ui.small}>{example.incoming}</Copy><Copy selectable style={ui.body}>{example.reply}</Copy>
        </View>)}
      </Card>}
      {hasMessages && <Copy style={ui.small}>{roomId ? 'Pamięć zostaje na telefonie również po skróceniu historii. Usunięcie historii lub odłączenie konta usuwa też jego pamięć.' : 'Podgląd korzysta z wiadomości zapisanych na telefonie.'}</Copy>}
    </>}
  </Page>;
}

const styles = StyleSheet.create({
  summary: { padding: 20, gap: 14, borderRadius: 20 },
  cardTitle: { fontFamily: 'DMSansSemiBold', fontSize: 16, lineHeight: 23 },
  avatar: { width: 42, height: 42, borderRadius: 21, alignItems: 'center', justifyContent: 'center' },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  tag: { paddingHorizontal: 11, paddingVertical: 7, borderRadius: 20 },
  chat: { borderWidth: 1, borderRadius: 22, overflow: 'hidden' },
  chatHeader: { padding: 16, borderBottomWidth: 1 },
  chatBody: { padding: 16, gap: 22 },
  exchange: { gap: 7 },
  divider: { textAlign: 'center', fontSize: 10, letterSpacing: 1.3, marginBottom: 8 },
  bubble: { maxWidth: '86%', paddingHorizontal: 15, paddingVertical: 11, borderRadius: 19 },
  incoming: { alignSelf: 'flex-start', borderBottomLeftRadius: 5 },
  outgoing: { alignSelf: 'flex-end', borderBottomRightRadius: 5 },
  message: { fontSize: 16, lineHeight: 23 },
  emptyChat: { paddingVertical: 28, alignItems: 'center', gap: 12 },
});
