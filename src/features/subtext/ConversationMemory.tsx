import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Row } from '@/components/ui';
import { subtext } from '@/services/subtext';
import { useTranslation } from '@/i18n';
import { useTheme } from '@/theme/useTheme';
import { usePreferences } from '@/features/preferences';
import { SettingsGroup, SettingsRow } from './SettingsRows';
import { Button, Disclosure, ErrorText, Page, ui } from './components';

export default function ConversationMemory({ id, name }: { id: string; name: string }) {
  const { colors } = useTheme(); const { t } = useTranslation();
  const [source, setSource] = useState<'aiMemory' | 'storedMemory'>('aiMemory');
  const query = useQuery({ queryKey: ['subtext', 'memory', id], queryFn: () => subtext.conversationMemory(id) });
  const client = useQueryClient();
  const developerMode = usePreferences(state => state.developerMode);
  const reset = useMutation({
    mutationFn: async () => {
      await client.cancelQueries({ queryKey: ['subtext'] });
      await subtext.resetConversation(id);
    },
    onSuccess: async () => {
      await client.resetQueries({ predicate: query => query.queryKey[0] === 'subtext' &&
        (query.queryKey[2] === id || query.queryKey[1] === 'writing-style') });
      await client.invalidateQueries({ queryKey: ['subtext'] });
    },
  });
  function confirmReset() {
    Alert.alert(t('memory.resetTitle', { name }), t('memory.resetMessage'), [
      { text: t('common.cancel'), style: 'cancel' },
      { text: t('memory.resetConfirm'), style: 'destructive', onPress: () => reset.mutate() },
    ]);
  }
  const stored = query.data?.storedMemory;
  const { seen, ...memoryWithoutIndex } = stored ?? {};
  const processedIds = seen && typeof seen === 'object' && !Array.isArray(seen) ? Object.keys(seen) : [];
  const memory = source === 'aiMemory' ? query.data?.aiMemory : memoryWithoutIndex;
  return <Page>
    <Copy style={ui.eyebrow}>{t('memory.eyebrow')}</Copy>
    <Copy title style={ui.title}>{name}</Copy>
    <Copy selectable style={ui.small}>{query.data?.conversationId ?? id}</Copy>
    {developerMode && subtext.supportsConversationReset() && <SettingsGroup title={t('memory.resetSection')} footer={t('memory.resetHint')}>
      <SettingsRow icon="trash" title={reset.isPending ? t('memory.resetting') : t('memory.reset')} destructive busy={reset.isPending} onPress={confirmReset} />
    </SettingsGroup>}
    {reset.isSuccess && <Copy accessibilityLiveRegion="polite" style={ui.small}>{t('memory.resetSuccess')}</Copy>}
    <ErrorText error={reset.error} />
    <Row style={{ flexWrap: 'wrap', gap: 8 }}>
      {(['aiMemory', 'storedMemory'] as const).map(value => <Pressable key={value} accessibilityRole="tab"
        accessibilityState={{ selected: source === value }} onPress={() => setSource(value)}
        style={{ minHeight: 44, padding: 10, borderRadius: 10, backgroundColor: source === value ? colors.text : colors.secondary }}>
        <Copy style={[ui.small, { color: source === value ? colors.background : colors.text }]}>{t(`memory.${value}`)}</Copy>
      </Pressable>)}
    </Row>
    <Copy style={ui.small}>{t(`memory.${source}Hint`)}</Copy>
    <Button label={query.isFetching ? t('memory.reading') : t('memory.refresh')} secondary disabled={query.isFetching} onPress={() => { void query.refetch(); }} />
    <ErrorText error={query.error} />
    {query.isPending ? <ActivityIndicator color={colors.text} /> : query.data && <>
      <Copy selectable style={{ fontFamily: 'monospace', fontSize: 12, lineHeight: 19, color: colors.text }}>{JSON.stringify(memory, null, 2)}</Copy>
      {source === 'storedMemory' && <Disclosure label={t('memory.processedIndex', { count: processedIds.length })}>
        <Copy style={ui.small}>{t('memory.processedIndexHint')}</Copy>
        <View><Copy selectable style={{ fontFamily: 'monospace', fontSize: 12, lineHeight: 19 }}>{JSON.stringify(Object.fromEntries(processedIds.slice(0, 40).map(key => [key, (seen as Record<string, unknown>)[key]])), null, 2)}</Copy></View>
      </Disclosure>}
    </>}
  </Page>;
}
