import { View, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, Copy, Icon, Row } from '@/components/ui';
import { CueMascot } from '@/components/CueBrand';
import { subtext, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import type { z } from 'zod';
import type { writingStyleSchema } from '@/types/subtext';
import { Button, Disclosure, ErrorText, Page, ui } from './components';

function countLabel(count: number, forms: [string, string, string]) {
  const few = count % 10 >= 2 && count % 10 <= 4 && !(count % 100 >= 12 && count % 100 <= 14);
  return `${count} ${forms[count === 1 ? 0 : few ? 1 : 2]}`;
}

type Example = z.infer<typeof writingStyleSchema>['examples'][number];

function Exchanges({ examples }: { examples: Example[] }) {
  const { colors } = useTheme();
  return <View style={{ gap: 12 }}>{examples.map(example => <View key={example.id} style={{ gap: 6 }}>
    {!!example.incoming && <View style={[styles.bubble, { alignSelf: 'flex-start', backgroundColor: colors.secondary }]}>
      <Copy selectable style={[ui.body, { color: colors.text }]}>{example.incoming}</Copy>
    </View>}
    <View style={[styles.bubble, { alignSelf: 'flex-end', backgroundColor: colors.accent }]}>
      <Copy selectable style={[ui.body, { color: colors.onAccent }]}>{example.reply}</Copy>
    </View>
  </View>)}</View>;
}

export default function WritingStyle({ roomId, personName, isExample = false }: { roomId?: string; personName?: string; isExample?: boolean }) {
  const { colors } = useTheme();
  const client = useQueryClient();
  const { data: status } = useSubtextStatus();
  const queryKey = ['subtext', 'writing-style', roomId ?? 'general'];
  const preview = useMutation({ mutationFn: () => roomId ? subtext.previewConversationWritingStyle(roomId) : subtext.previewWritingStyle(), onSuccess: result => {
    client.setQueryData(queryKey, result);
  } });
  const query = useQuery({ queryKey, queryFn: () => roomId ? subtext.conversationWritingStyle(roomId) : subtext.writingStyle() });
  const style = query.data;
  const examples = style?.examples ?? [];
  const memories = style?.relationship ?? [];

  return <Page compact>
    <ErrorText error={query.error ?? preview.error} />
    {query.isError && !style && <Button label="Spróbuj ponownie" secondary onPress={() => { void query.refetch(); }} />}
    {query.isPending ? <Copy style={ui.small}>Wczytuję…</Copy> : style && <>
      <Card style={styles.card}>
        <Row style={{ gap: 12 }}>
          <CueMascot pose="read" size={64} />
          <View style={{ flex: 1, gap: 3 }}>
            <Copy style={[styles.title, { color: colors.text }]}>{roomId ? personName : 'Cechy i nawyki'}</Copy>
            <Copy style={ui.small}>{isExample ? 'Przykład nie wpływa na Twój styl.' : style.sampleCount ? `${countLabel(style.sampleCount, ['wiadomość', 'wiadomości', 'wiadomości'])}${!roomId ? ` · ${countLabel(style.conversationCount, ['rozmowa', 'rozmowy', 'rozmów'])}` : ''}` : 'Za mało wiadomości, żeby określić styl.'}</Copy>
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel="Odśwież pamięć i styl" disabled={query.isFetching}
            onPress={() => { void query.refetch(); }} style={styles.refresh}>
            {query.isFetching ? <ActivityIndicator color={colors.accent} /> : <Icon name="refresh" size={20} color={colors.accent} />}
          </Pressable>
        </Row>
        {!!style.summary && <Copy style={ui.body}>{style.summary}</Copy>}
        {style.habits.length ? <View style={styles.tags}>{style.habits.map(habit => <View key={habit} style={[styles.tag, { backgroundColor: colors.secondary }]}>
          <Copy style={[ui.small, { color: colors.text }]}>{habit}</Copy>
        </View>)}</View> : <Copy style={ui.small}>{isExample ? 'Twój styl poznam z Twoich prawdziwych rozmów.' : style.sampleCount ? 'Potrzebuję jeszcze kilku Twoich wiadomości.' : roomId ? 'Zsynchronizuj rozmowę, żeby poznać swój styl.' : 'Połącz konto, żeby poznać swój styl.'}</Copy>}
        {!roomId && !style.sampleCount && <Button label="Połącz konto" onPress={() => router.push('/connections')} />}
      </Card>

      <Card style={styles.card}>
        <Copy style={[styles.title, { color: colors.text }]}>Najczęstsze zwroty</Copy>
        <Copy style={ui.small}>Z prawdziwych wiadomości · liczba wiadomości zawierających zwrot.</Copy>
        {style.phrases?.length ? <View style={styles.tags}>{style.phrases.map(phrase =>
          <View key={phrase.text} style={[styles.tag, { backgroundColor: colors.secondary }]}>
            <Copy selectable style={[ui.small, { color: colors.text }]}>{phrase.text} · {phrase.count}×</Copy>
          </View>)}</View> : <Copy style={ui.small}>Zwroty pojawią się, gdy powtórzą się w co najmniej 3 wiadomościach.</Copy>}
      </Card>

      {roomId && !isExample && <Card style={styles.card}>
        <Copy style={[styles.title, { color: colors.text }]}>Co Cue pamięta</Copy>
        {memories.length ? <>
          {memories.slice(0, 3).map(item => <Copy key={item.id} selectable style={ui.body}>{item.text}</Copy>)}
          {memories.length > 3 && <Disclosure label={`Więcej ustaleń (${memories.length - 3})`} small>{memories.slice(3).map(item => <Copy key={item.id} selectable style={ui.body}>{item.text}</Copy>)}</Disclosure>}
        </> : <Copy style={ui.small}>{status?.cloudEnabled ? 'Ustalenia pojawią się po analizie rozmowy.' : 'Włącz AI, żeby zapamiętywać ustalenia.'}</Copy>}
        {status?.analyzing === roomId && <Row style={{ gap: 8 }}><ActivityIndicator size="small" color={colors.accent} /><Copy style={ui.small}>Uzupełniam pamięć…</Copy></Row>}
        {!!style.contextError && <Copy accessibilityRole="alert" style={[ui.small, { color: colors.warning }]}>Aktualizacja się nie udała. Spróbujemy ponownie.</Copy>}
      </Card>}

      <Card style={styles.card}>
        <Copy style={[styles.title, { color: colors.text }]}>Przykładowe odpowiedzi</Copy>
        <Copy style={ui.small}>Te same 6 wiadomości dla każdego stylu, żeby łatwo porównać odpowiedzi.</Copy>
        {style.previewExamples?.length ? <>
          <Copy style={[ui.small, { color: colors.accent }]}>Wygenerowane w tym stylu</Copy>
          <Exchanges examples={style.previewExamples.slice(0, 3)} />
          {style.previewExamples.length > 3 && <Disclosure label="Zobacz więcej" small><Exchanges examples={style.previewExamples.slice(3)} /></Disclosure>}
        </> : <Copy style={ui.small}>Wygeneruj odpowiedzi na przywitanie, zaproszenie, dobrą wiadomość, ciężki dzień, spóźnienie i plany na weekend.</Copy>}
      </Card>

      {!!examples.length && <Disclosure label="Wiadomości z rozmów" small>
        <Exchanges examples={examples} />
      </Disclosure>}

      {style.sampleCount >= 5 && <>
        {status?.cloudEnabled ? <Button label={preview.isPending ? 'Przygotowuję przykłady…' : style.generated || style.previewExamples?.length ? 'Odśwież przykłady AI' : 'Zobacz przykłady AI'}
          secondary disabled={!!status.analyzing || preview.isPending} onPress={() => preview.mutate()} /> : <Button label="Włącz analizę AI" secondary onPress={() => router.push('/settings')} />}
      </>}

      {!isExample && <Disclosure label="Jak działa pamięć?" small>
        <Copy style={ui.small}>{roomId ? 'Styl liczymy lokalnie z maksymalnie 60 ostatnich próbek. Ustalenia AI są osobne dla tego czatu. Usunięcie danych lub odłączenie konta usuwa też jego pamięć.' : 'Styl opiera się na Twoich wiadomościach zapisanych na telefonie. Przykłady AI są podglądem i nie wpływają na pamięć.'}</Copy>
        <Copy style={ui.small}>{status?.cloudEnabled ? 'AI przesyła próbki przez Supabase do DeepSeek.' : 'AI jest wyłączone. Styl nadal aktualizuje się na telefonie.'}</Copy>
        {style.traits?.map(trait => <Copy key={trait.text} style={ui.small}>{trait.text}: {trait.matches} z {trait.sampleSize} próbek</Copy>)}
        {!!style.pendingMessages && <Copy style={ui.small}>{style.pendingMessages} nowych wiadomości czeka na aktualizację kontekstu.</Copy>}
        {!!style.contextUpdatedAt && <Copy style={ui.small}>Aktualizacja kontekstu: {new Date(style.contextUpdatedAt).toLocaleString('pl-PL')}</Copy>}
        {memories.map(item => <Copy key={item.id} style={ui.small}>{item.text} · {item.evidenceIds.length} źródeł · {new Date(item.updatedAt).toLocaleDateString('pl-PL')}</Copy>)}
        {!!style.contextError && <Copy style={ui.small}>{style.contextError}</Copy>}
      </Disclosure>}
    </>}
  </Page>;
}

const styles = StyleSheet.create({
  card: { padding: 16, gap: 10, borderRadius: 20, borderCurve: 'continuous' },
  title: { fontFamily: 'DMSansSemiBold', fontSize: 17, lineHeight: 24 },
  refresh: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  tag: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 16 },
  bubble: { maxWidth: '90%', paddingHorizontal: 12, paddingVertical: 9, borderRadius: 16 },
});
