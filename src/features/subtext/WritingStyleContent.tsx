import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Icon, Row } from '@/components/ui';
import { subtext, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import type { z } from 'zod';
import type { writingStyleSchema, WritingTone } from '@/types/subtext';
import { Button, Disclosure, ErrorText } from './components';

export type WritingStyleProps = { roomId?: string; isExample?: boolean };
type Example = z.infer<typeof writingStyleSchema>['examples'][number];

const tones: { id: WritingTone; label: string; description: string }[] = [
  { id: 'natural', label: 'Naturalny', description: 'Tak, jak zwykle piszesz — bez dodatkowego tonu.' },
  { id: 'flirt', label: 'Flirtujący', description: 'Subtelne zainteresowanie, ciepło i lekki humor.' },
  { id: 'assertive', label: 'Asertywny', description: 'Jasne potrzeby i granice, spokojnie i bez agresji.' },
  { id: 'empathetic', label: 'Empatyczny', description: 'Zrozumienie emocji i wsparcie bez pustych pocieszeń.' },
  { id: 'calming', label: 'Łagodzący konflikt', description: 'Spokojna odpowiedź, która pomaga dojść do porozumienia.' },
];

function countLabel(count: number, forms: [string, string, string]) {
  const few = count % 10 >= 2 && count % 10 <= 4 && !(count % 100 >= 12 && count % 100 <= 14);
  return `${count} ${forms[count === 1 ? 0 : few ? 1 : 2]}`;
}

function Exchanges({ examples }: { examples: Example[] }) {
  const { colors } = useTheme();
  return <View style={{ gap: 16 }}>{examples.map(example => <View key={example.id} style={{ gap: 6 }}>
    {!!example.incoming && <View style={[styles.bubble, { alignSelf: 'flex-start', backgroundColor: colors.secondary }]}>
      <Copy selectable style={[styles.example, { color: colors.text }]}>{example.incoming}</Copy>
    </View>}
    <View style={[styles.bubble, { alignSelf: 'flex-end', backgroundColor: colors.accent }]}>
      <Copy selectable style={[styles.example, { color: colors.onAccent }]}>{example.reply}</Copy>
    </View>
  </View>)}</View>;
}

/** Shared content, with one scroll owner when embedded in a person's screen. */
export default function WritingStyleContent({ roomId, isExample = false }: WritingStyleProps) {
  const { colors } = useTheme();
  const client = useQueryClient();
  const { data: status } = useSubtextStatus();
  const queryKey = ['subtext', 'writing-style', roomId ?? 'general'];
  const query = useQuery({ queryKey, queryFn: () => roomId ? subtext.conversationWritingStyle(roomId) : subtext.writingStyle() });
  const preview = useMutation({
    mutationFn: () => roomId ? subtext.previewConversationWritingStyle(roomId) : subtext.previewWritingStyle(),
    onSuccess: result => { client.setQueryData(queryKey, result); },
  });
  const selection = useMutation({
    mutationFn: (tone: WritingTone) => subtext.setWritingTone(roomId, tone),
    onSuccess: result => { preview.reset(); client.setQueryData(queryKey, result); },
  });
  const style = query.data;
  const examples = style?.examples ?? [];
  const previews = style?.previewExamples ?? [];
  const tone = tones.find(item => item.id === style?.selectedTone) ?? tones[0];
  const busy = selection.isPending || preview.isPending || !!status?.analyzing;

  return <View style={{ gap: 4 }}>
    <View style={[styles.section, { paddingTop: roomId ? 20 : 0, borderColor: colors.border, borderTopWidth: roomId ? StyleSheet.hairlineWidth : 0 }]}>
      <Row style={{ justifyContent: 'space-between', gap: 12 }}>
        <View style={{ flex: 1, gap: 3 }}>
          <Copy accessibilityRole="header" style={[styles.title, { color: colors.text }]}>{roomId ? 'Twój styl' : 'Cechy Twoich wiadomości'}</Copy>
          {style && <Copy style={styles.caption}>{isExample ? 'Rozmowa przykładowa' : style.sampleCount ?
            `Na podstawie ${style.sampleCount} wiadomości${!roomId ? ` · ${countLabel(style.conversationCount, ['rozmowa', 'rozmowy', 'rozmów'])}` : ''}` : 'Styl pojawi się wraz z Twoimi wiadomościami'}</Copy>}
        </View>
        {!roomId && <Pressable accessibilityRole="button" accessibilityLabel="Odśwież styl pisania" accessibilityState={{ disabled: query.isFetching, busy: query.isFetching }} disabled={query.isFetching}
          onPress={() => { void query.refetch(); }} style={({ pressed }) => [styles.refresh, { opacity: pressed ? .6 : 1 }]}>
          {query.isFetching ? <ActivityIndicator size="small" color={colors.accent} /> : <Icon name="refresh" size={18} color={colors.accent} />}
        </Pressable>}
      </Row>
      <ErrorText error={query.error} />
      {query.isPending ? <Copy style={styles.caption}>Odczytuję styl…</Copy> : style && <View style={{ gap: 10 }}>
        {!!style.summary && <Copy style={styles.body}>{style.summary}</Copy>}
        {style.habits.length ? <View style={{ gap: 9 }}>{style.habits.map(habit => <Row key={habit} style={{ alignItems: 'flex-start', gap: 10 }}>
          <Copy style={[styles.habit, { color: colors.accent }]}>—</Copy>
          <Copy style={[styles.habit, { color: colors.text, flex: 1 }]}>{habit}</Copy>
        </Row>)}</View> : <Copy style={styles.body}>{isExample ? 'Twój styl poznam z prawdziwych rozmów.' : style.sampleCount ? 'Potrzebuję jeszcze kilku Twoich wiadomości.' : roomId ? 'Napisz kilka wiadomości w tej rozmowie, żeby poznać swój styl.' : 'Połącz konto, żeby poznać swój styl.'}</Copy>}
        {!roomId && !style.sampleCount && <Button label="Połącz konto" onPress={() => router.push('/connections')} />}
      </View>}
      {query.isError && !style && <Button label="Spróbuj ponownie" secondary onPress={() => { void query.refetch(); }} />}
    </View>

    {!roomId && !!examples.length && <View style={[styles.savedExamples, { borderColor: colors.border }]}>
      <View style={{ gap: 4 }}>
        <Copy accessibilityRole="header" style={[styles.title, { color: colors.text }]}>Z Twoich rozmów</Copy>
        <Copy style={styles.caption}>Twoje odpowiedzi, w Twoim stylu</Copy>
      </View>
      <Exchanges examples={examples.slice(0, 2)} />
      {examples.length > 2 && <Disclosure label="Więcej Twoich odpowiedzi" small><Exchanges examples={examples.slice(2)} /></Disclosure>}
    </View>}

    {style && <View style={[styles.examplesSection, { borderColor: colors.border }]}>
      <View style={{ gap: 10, paddingVertical: 16 }}>
        <Copy style={[styles.title, { color: colors.text }]}>Styl odpowiedzi</Copy>
        <Copy style={styles.body}>Wybierz ton. Odpowiedzi zachowają Twój sposób pisania: wielkość liter, długość, skróty i emoji.</Copy>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {tones.map(item => <Pressable key={item.id} accessibilityRole="radio"
            accessibilityState={{ checked: tone.id === item.id, disabled: busy || !status?.available }}
            disabled={busy || !status?.available} onPress={() => selection.mutate(item.id)}
            style={({ pressed }) => ({ minHeight: 44, justifyContent: 'center', borderRadius: 22, paddingHorizontal: 14, paddingVertical: 10,
              backgroundColor: tone.id === item.id ? colors.accent : colors.secondary, opacity: busy ? .5 : pressed ? .7 : 1 })}>
            <Copy style={{ fontSize: 14, color: tone.id === item.id ? colors.onAccent : colors.text }}>{item.label}</Copy>
          </Pressable>)}
        </View>
        <Copy style={styles.caption}>{selection.isPending ? 'Zapisuję wybór…' : tone.description}</Copy>
        <ErrorText error={selection.error} />
      </View>
      <Disclosure label="Przykładowe odpowiedzi">
        <Copy style={styles.caption}>Wybrany styl: {tone.label.toLocaleLowerCase('pl')}</Copy>
        <ErrorText error={preview.error} />
        {previews.length ? <Exchanges examples={previews} /> : <Copy style={styles.body}>Zobacz, jak mogłaby brzmieć odpowiedź napisana w Twoim stylu.</Copy>}
        {style.sampleCount >= 5 ? status?.cloudEnabled ?
          <Button label={preview.isPending ? 'Przygotowuję przykłady…' : previews.length ? 'Odśwież przykłady' : 'Pokaż przykłady'}
            secondary disabled={busy} onPress={() => preview.mutate()} /> :
          <Button label="Włącz analizę AI" secondary onPress={() => router.push('/settings')} /> :
          <Copy style={styles.caption}>Przykłady będą dostępne po kilku Twoich wiadomościach.</Copy>}
      </Disclosure>
    </View>}
  </View>;
}

const styles = StyleSheet.create({
  section: { paddingBottom: 16, gap: 14 },
  title: { fontFamily: 'DMSansSemiBold', fontSize: 17, lineHeight: 24 },
  caption: { fontSize: 12, lineHeight: 18 },
  body: { fontSize: 14, lineHeight: 21 },
  habit: { fontSize: 14, lineHeight: 21 },
  refresh: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  examplesSection: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: 4 },
  savedExamples: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: 20, paddingBottom: 12, gap: 16 },
  bubble: { maxWidth: '90%', paddingHorizontal: 12, paddingVertical: 9, borderRadius: 14, borderCurve: 'continuous' },
  example: { fontSize: 14, lineHeight: 21 },
});
