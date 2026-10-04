import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Icon, Row } from '@/components/ui';
import { subtext, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import type { z } from 'zod';
import type { writingStyleSchema, WritingTone } from '@/types/subtext';
import { Button, Disclosure, ErrorText } from './components';
import { currentLanguage, plural, useTranslation } from '@/i18n';
import EvidenceSources from './EvidenceSources';

export type WritingStyleProps = { roomId?: string; isExample?: boolean };
type Example = z.infer<typeof writingStyleSchema>['examples'][number];

const tones: WritingTone[] = ['natural', 'flirt', 'assertive', 'empathetic', 'calming'];

function Exchanges({ examples }: { examples: Example[] }) {
  const { colors } = useTheme(); const { t } = useTranslation();
  return <View style={styles.exchanges}>{examples.map((example, index) => (
    <View key={example.id} style={[styles.exchange, {
      borderColor: colors.border,
      borderTopWidth: index ? StyleSheet.hairlineWidth : 0,
      paddingTop: index ? 24 : 0,
    }]}>
      {!!example.incoming && <View style={[styles.bubble, styles.incoming, { backgroundColor: colors.surface }]}>
        <Copy selectable accessibilityLabel={t('style.incomingLabel', { text: example.incoming })} style={[styles.message, { color: colors.text }]}>{example.incoming}</Copy>
      </View>}
      <View style={[styles.bubble, styles.reply, { backgroundColor: colors.accent }]}>
        <Copy selectable accessibilityLabel={t('style.replyLabel', { text: example.reply })} style={[styles.message, { color: colors.onAccent }]}>{example.reply}</Copy>
      </View>
    </View>
  ))}</View>;
}

/** Shared content, with one scroll owner when embedded in a person's screen. */
export default function WritingStyleContent({ roomId, isExample = false }: WritingStyleProps) {
  const { colors } = useTheme(); const { t } = useTranslation();
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
  const previews = style?.previewExamples ?? [];
  const tone = tones.find(item => item === style?.selectedTone) ?? tones[0];
  const busy = selection.isPending || preview.isPending || !!status?.analyzing;
  const previewContent = style && <View style={{ gap: 16 }}>
    {!previews.length && <Copy style={styles.body}>{t('style.previewIntro')}</Copy>}
    <Copy style={styles.caption}>{t('style.selected', { tone: t(`style.tone.${tone}.label`).toLocaleLowerCase(currentLanguage()) })}</Copy>
    <ErrorText error={preview.error} />
    {style.sampleCount >= 5 ? status?.cloudEnabled ?
      <Button label={preview.isPending ? t('style.preparing') : previews.length ? t('style.refreshExamples') : t('style.showExamples')}
        secondary={!!roomId} disabled={busy} onPress={() => preview.mutate()} /> :
      <Button label={t('style.enableAI')} secondary onPress={() => router.push('/settings')} /> :
      <Copy style={styles.caption}>{t('style.examplesLater')}</Copy>}
    {!!previews.length && <Exchanges examples={previews} />}
  </View>;

  return <View style={{ gap: 4 }}>
    {roomId && !!style?.relationship?.length && <View style={styles.section}>
      <Copy accessibilityRole="header" style={[styles.title, { color: colors.text }]}>Kontekst rozmowy</Copy>
      {style.relationship.map(item => <View key={item.id} style={{ gap: 4 }}>
        <Copy selectable style={[styles.body, { color: colors.text }]}>{item.text}</Copy>
        <EvidenceSources roomId={roomId} evidenceIds={item.evidenceIds} sources={item.sources} />
      </View>)}
    </View>}
    <View style={[styles.section, { paddingTop: roomId ? 20 : 0, borderColor: colors.border, borderTopWidth: roomId ? StyleSheet.hairlineWidth : 0 }]}>
      <Row style={{ justifyContent: 'space-between', gap: 12 }}>
        <View style={{ flex: 1, gap: 3 }}>
          <Copy accessibilityRole="header" style={[styles.title, { color: colors.text }]}>{roomId ? t('style.yourStyle') : t('style.traits')}</Copy>
          {style && <Copy style={styles.caption}>{isExample ? t('common.exampleConversation') : style.sampleCount ?
            `${t('style.basedOn', { count: style.sampleCount })}${!roomId ? ` · ${plural('style.conversations', style.conversationCount)}` : ''}` : t('style.appearsLater')}</Copy>}
        </View>
        {!roomId && <Pressable accessibilityRole="button" accessibilityLabel={t('style.refresh')} accessibilityState={{ disabled: query.isFetching, busy: query.isFetching }} disabled={query.isFetching}
          onPress={() => { void query.refetch(); }} style={({ pressed }) => [styles.refresh, { opacity: pressed ? .6 : 1 }]}>
          {query.isFetching ? <ActivityIndicator size="small" color={colors.accent} /> : <Icon name="refresh" size={18} color={colors.accent} />}
        </Pressable>}
      </Row>
      <ErrorText error={query.error} />
      {query.isPending ? <Copy style={styles.caption}>{t('style.reading')}</Copy> : style && <View style={{ gap: 10 }}>
        {!!style.summary && <Copy style={styles.body}>{style.summary}</Copy>}
        {style.habits.length ? <View style={{ gap: 9 }}>{style.habits.map(habit => <Row key={habit} style={{ alignItems: 'flex-start', gap: 10 }}>
          <Copy style={[styles.habit, { color: colors.accent }]}>—</Copy>
          <Copy style={[styles.habit, { color: colors.text, flex: 1 }]}>{habit}</Copy>
        </Row>)}</View> : <Copy style={styles.body}>{isExample ? t('style.exampleOnly') : style.sampleCount ? t('style.needMore') : roomId ? t('style.writeInConversation') : t('style.connectToLearn')}</Copy>}
        {!roomId && !style.sampleCount && <Button label={t('style.connectAccount')} onPress={() => router.push('/connections')} />}
      </View>}
      {query.isError && !style && <Button label={t('common.retry')} secondary onPress={() => { void query.refetch(); }} />}
    </View>

    {style && <View style={[styles.examplesSection, { borderColor: colors.border }]}>
      <View style={{ gap: 10 }}>
        <Copy accessibilityRole="header" style={[styles.title, { color: colors.text }]}>{roomId ? t('style.replyStyle') : t('style.tryStyle')}</Copy>
        <Copy style={styles.body}>{t('style.chooseTone')}</Copy>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {tones.map(item => <Pressable key={item} accessibilityRole="radio"
            accessibilityState={{ checked: tone === item, disabled: busy || !status?.available }}
            disabled={busy || !status?.available} onPress={() => selection.mutate(item)}
            style={({ pressed }) => ({ minHeight: 44, justifyContent: 'center', borderRadius: 22, paddingHorizontal: 14, paddingVertical: 10,
              backgroundColor: tone === item ? colors.accent : colors.secondary, opacity: busy ? .5 : pressed ? .7 : 1 })}>
            <Copy style={{ fontSize: 14, color: tone === item ? colors.onAccent : colors.text }}>{t(`style.tone.${item}.label`)}</Copy>
          </Pressable>)}
        </View>
        <Copy style={styles.caption}>{selection.isPending ? t('style.saving') : t(`style.tone.${tone}.description`)}</Copy>
        <ErrorText error={selection.error} />
      </View>
      {roomId ? <Disclosure label={t('style.sampleReplies')}>{previewContent}</Disclosure> : previewContent}
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
  examplesSection: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: 20, gap: 12 },
  exchanges: { gap: 24 },
  exchange: { gap: 8 },
  bubble: { maxWidth: '88%', paddingHorizontal: 12, paddingVertical: 10, borderRadius: 18, borderCurve: 'continuous' },
  incoming: { alignSelf: 'flex-start', borderBottomLeftRadius: 5 },
  reply: { alignSelf: 'flex-end', borderBottomRightRadius: 5 },
  message: { fontSize: 14, lineHeight: 21 },
});
