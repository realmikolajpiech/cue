import { MessagePhoto } from './MessagePhoto';
import { useMemo } from 'react';
import { View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { Redirect, Stack, useLocalSearchParams } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy } from '@/components/ui';
import { useRoom, useSubtextStatus, useSyncRoom } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, ui } from './components';
import { messageText, conversationTime } from './conversationPresentation';
import { useTranslation } from '@/i18n';

export default function Messages() {
  const { id } = useLocalSearchParams<{ id: string }>(); const query = useRoom(id); const { colors } = useTheme(); const insets = useSafeAreaInsets();
  const { data: status } = useSubtextStatus(); const { t } = useTranslation();
  const room = query.data;
  const sync = useSyncRoom(id, !!room && !room.demo && status?.[room.network].phase === 'CONNECTED', room?.updatedAt);
  const messages = useMemo(() => [...(query.data?.messages ?? [])].sort((a, b) => a.timestamp - b.timestamp), [query.data?.messages]);
  if (query.isSuccess && !query.data) return <Redirect href="/(tabs)" />;
  return <View style={{ flex: 1, backgroundColor: colors.background }}>
    <Stack.Screen options={{ title: t('nav.messages'), headerBackTitle: query.data?.name ?? t('common.conversation') }} />
    <View style={{ gap: 12, paddingHorizontal: 20, paddingVertical: 12 }}>
      <Copy style={ui.small}>{t('messages.intro')}</Copy>
      {query.error && <><ErrorText error={query.error} /><Button label={t('common.retry')} secondary onPress={() => { void query.refetch(); }} /></>}
      <Button label={sync.isFetching ? t('messages.updating') : t('messages.refresh')} secondary disabled={sync.isFetching || !!room?.demo} onPress={() => { void sync.refetch(); }} />
      {sync.error && <ErrorText error={sync.error} />}
    </View>
    <FlashList data={messages} keyExtractor={message => message.id} refreshing={sync.isFetching} onRefresh={() => { void sync.refetch(); }} maintainVisibleContentPosition={{ startRenderingFromBottom: true }}
      contentContainerStyle={{ padding: 20, paddingBottom: insets.bottom + 24 }}
      renderItem={({ item }) => <View style={{ gap: 6, marginBottom: 16, alignItems: item.isMe ? 'flex-end' : 'flex-start' }}>
        <Copy style={ui.small}>{item.isMe ? t('common.you') : item.sender || query.data?.name} · {conversationTime(item.timestamp)}</Copy>
        <View style={{ maxWidth: '90%', padding: 14, borderRadius: 16, borderCurve: 'continuous', backgroundColor: item.isMe ? colors.accent : colors.surface }}>
          {item.text.startsWith('[Zdjęcie]') && <MessagePhoto roomId={id} messageId={item.id} />}
          <Copy selectable style={[ui.body, { color: item.isMe ? colors.onAccent : colors.text }]}>{messageText(item.text)}</Copy>
        </View>
      </View>}
      ListEmptyComponent={<Copy style={ui.body}>{query.isPending ? t('messages.opening') : query.data?.historyNotice ?? t('messages.empty')}</Copy>} />
  </View>;
}
