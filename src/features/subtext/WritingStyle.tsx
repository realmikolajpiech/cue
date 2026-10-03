import { View, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, Copy, Icon, Row } from '@/components/ui';
import { subtext, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, Page, ui } from './components';

export default function WritingStyle() {
  const { colors } = useTheme();
  const client = useQueryClient();
  const { data: status } = useSubtextStatus();
  const preview = useMutation({ mutationFn: subtext.previewWritingStyle, onSuccess: result => {
    client.setQueryData(['subtext', 'writing-style'], result);
  } });
  const query = useQuery({ queryKey: ['subtext', 'writing-style'], queryFn: subtext.writingStyle });
  const style = query.data;
  const hasMessages = !!style?.sampleCount;
  const examples = style?.examples ?? [];

  return <Page>
    <View style={{ gap: 10 }}>
      <Copy style={ui.eyebrow}>Tak piszesz Ty</Copy>
      <Copy title style={ui.heading}>Twój styl.</Copy>
      <Copy style={ui.body}>Twój sposób pisania, zebrany z różnych rozmów. Zobacz, jak brzmi na co dzień.</Copy>
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
        <Copy style={ui.small}>{style.sampleCount} próbek Twoich wiadomości · {style.conversationCount} {style.conversationCount === 1 ? 'rozmowa' : 'rozmowy'}</Copy>
        {status?.cloudEnabled ? <>
          <Button label={preview.isPending ? 'Przygotowuję 5 rozmów…' : style.generated ? 'Odśwież przykłady' : 'Zobacz 5 przykładowych rozmów'}
            disabled={style.sampleCount < 5 || !!status.analyzing || preview.isPending} onPress={() => preview.mutate()} />
          <Copy style={ui.small}>{style.sampleCount < 5 ? 'Potrzebujemy przynajmniej 5 Twoich wiadomości.' : 'AI użyje Twoich wiadomości z różnych rozmów. Próbki stylu trafią przez Supabase do DeepSeek.'}</Copy>
        </> : <Button label="Włącz analizę AI" secondary onPress={() => router.navigate('/alerts')} />}
      </Card> : <Card style={styles.summary}>
        <Icon name="style" size={28} />
        <Copy title style={ui.title}>Zacznijmy od Twoich wiadomości</Copy>
        <Copy style={ui.body}>Połącz Messenger lub WhatsApp i zsynchronizuj rozmowy. Twój styl powstaje z wiadomości, które piszesz Ty.</Copy>
        <Button label="Połącz konto" onPress={() => router.push('/connections')} />
      </Card>}

      <View style={{ gap: 6 }}>
        <Copy title style={ui.title}>Tak brzmi to w rozmowie</Copy>
        <Copy style={ui.small}>{style.generated ? 'Pięć codziennych sytuacji. Odpowiedzi AI w Twoim stylu — to przykłady, które możesz przeczytać jak czat.' : examples.length ? 'Autentyczne wymiany z Twoich rozmów. Utwórz przykłady, aby zobaczyć codzienne sytuacje w swoim stylu.' : 'Tutaj pojawi się do pięciu krótkich wymian w Twoim stylu.'}</Copy>
      </View>
      <View style={[styles.chat, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        <Row style={[styles.chatHeader, { borderBottomColor: colors.border }]}>
          <View style={[styles.avatar, { backgroundColor: colors.secondary }]}><Icon name="message" size={20} /></View>
          <View style={{ flex: 1, gap: 2 }}><Copy style={styles.cardTitle}>Twój styl w rozmowie</Copy><Copy style={ui.small}>Podgląd · tylko dla Ciebie</Copy></View>
        </Row>
        <View style={styles.chatBody}>
          {examples.length ? examples.map((example, index) => <View key={example.id} style={styles.exchange}>
            <Copy style={[ui.small, styles.divider]}>ROZMOWA {index + 1}</Copy>
            <View style={[styles.bubble, styles.incoming, { backgroundColor: colors.secondary }]}>
              <Copy selectable style={[styles.message, { color: colors.text }]}>{example.incoming}</Copy>
            </View>
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
      {hasMessages && <Copy style={ui.small}>Podgląd korzysta z wiadomości zapisanych na telefonie. Po kolejnej synchronizacji możesz odświeżyć go przyciskiem w karcie stylu.</Copy>}
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
