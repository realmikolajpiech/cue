import { useMemo } from 'react';
import { View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy } from '@/components/ui';
import { useRoom, useSubtextStatus, useSyncRoom } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, ui } from './components';
import { conversationTime } from './conversationPresentation';

export default function Messages() {
  const { id } = useLocalSearchParams<{ id: string }>(); const query = useRoom(id); const { colors } = useTheme(); const insets = useSafeAreaInsets();
  const { data: status } = useSubtextStatus();
  const room = query.data;
  const sync = useSyncRoom(id, !!room && !room.demo && status?.[room.network].phase === 'CONNECTED', room?.updatedAt);
  const messages = useMemo(() => [...(query.data?.messages ?? [])].sort((a, b) => a.timestamp - b.timestamp), [query.data?.messages]);
  return <View style={{ flex: 1, backgroundColor: colors.background }}>
    <Stack.Screen options={{ title: 'Wiadomości', headerBackTitle: query.data?.name ?? 'Rozmowa' }} />
    <View style={{ gap: 12, paddingHorizontal: 20, paddingVertical: 12 }}>
      <Copy style={ui.small}>Zapisane wiadomości są dostępne od razu. Nowe wiadomości aktualizują się w tle.</Copy>
      {query.error && <><ErrorText error={query.error} /><Button label="Spróbuj ponownie" secondary onPress={() => { void query.refetch(); }} /></>}
      <Button label={sync.isFetching ? 'Aktualizuję wiadomości…' : 'Odśwież wiadomości'} secondary disabled={sync.isFetching || !!room?.demo} onPress={() => { void sync.refetch(); }} />
      {sync.error && <ErrorText error={sync.error} />}
    </View>
    <FlashList data={messages} keyExtractor={message => message.id} refreshing={sync.isFetching} onRefresh={() => { void sync.refetch(); }} maintainVisibleContentPosition={{ startRenderingFromBottom: true }}
      contentContainerStyle={{ padding: 20, paddingBottom: insets.bottom + 24 }}
      renderItem={({ item }) => <View style={{ gap: 6, marginBottom: 16, alignItems: item.isMe ? 'flex-end' : 'flex-start' }}>
        <Copy style={ui.small}>{item.isMe ? 'Ty' : item.sender || query.data?.name} · {conversationTime(item.timestamp)}</Copy>
        <View style={{ maxWidth: '90%', padding: 14, borderRadius: 16, borderCurve: 'continuous', backgroundColor: item.isMe ? colors.accent : colors.surface }}>
          <Copy selectable style={[ui.body, { color: item.isMe ? colors.onAccent : colors.text }]}>{item.text}</Copy>
        </View>
      </View>}
      ListEmptyComponent={<Copy style={ui.body}>{query.isPending ? 'Otwieram wiadomości…' : query.data?.historyNotice ?? 'Nie ma jeszcze zapisanych wiadomości.'}</Copy>} />
  </View>;
}
