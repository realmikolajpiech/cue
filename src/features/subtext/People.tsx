import { useDeferredValue, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { Copy, Icon, Row } from '@/components/ui';
import { CueCompanion, CueMascot } from '@/components/CueBrand';
import { subtext, subtextCache, useRooms, useSubtextAction, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, ui } from './components';
import { ConversationRow } from './ConversationRow';
import { filterConversations, type InboxFilter } from './conversationPresentation';
import { useSubtextPreferences } from './preferences';
import type { Room } from '@/types/subtext';

const filters = [{ id: 'all', label: 'Wszystkie' }, { id: 'messenger', label: 'Messenger' }, { id: 'whatsapp', label: 'WhatsApp' }] as const;

export default function People() {
  const rooms = useRooms(); const action = useSubtextAction(); const { data: status } = useSubtextStatus(); const { colors } = useTheme();
  const [search, setSearch] = useState(''); const deferredSearch = useDeferredValue(search);
  const [filter, setFilter] = useState<InboxFilter>('all'); const input = useRef<TextInput>(null);
  const select = useSubtextPreferences(s => s.selectPerson);
  const people = useMemo(() => filterConversations(rooms.data ?? [], deferredSearch, filter), [rooms.data, deferredSearch, filter]);
  const hasRooms = !!rooms.data?.length;
  function open(room: Room) {
    Keyboard.dismiss();
    select(room.id);
    if (!subtextCache.getQueryData(['subtext', 'room', room.id])) subtextCache.setQueryData(['subtext', 'room', room.id], room);
    router.push({ pathname: '/person/[id]', params: { id: room.id } });
  }
  function refresh() { action.mutate(subtext.refresh); }
  return <View style={[styles.screen, { backgroundColor: colors.background }]}>
    <View style={styles.controls}>
      <CueCompanion title="Hej, jestem Cue" text="Wybierz rozmowę. Pomogę Ci złapać kontekst i znaleźć słowa." />
      <View style={[styles.search, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        <Icon name="search" size={20} color={colors.secondaryText} />
        <TextInput ref={input} accessibilityLabel="Szukaj rozmowy" placeholder="Szukaj osoby" placeholderTextColor={colors.secondaryText} cursorColor={colors.accent} selectionColor={colors.accent}
          onChangeText={setSearch} autoCorrect={false} autoCapitalize="none" returnKeyType="search" onSubmitEditing={() => input.current?.blur()}
          style={[styles.searchInput, { color: colors.text }]} />
        {!!search && <Pressable accessibilityRole="button" accessibilityLabel="Wyczyść wyszukiwanie" onPress={() => { input.current?.clear(); setSearch(''); }} style={styles.clear}><Icon name="close" size={18} color={colors.secondaryText} /></Pressable>}
      </View>
      <Row style={{ gap: 8, flexWrap: 'wrap' }}>{filters.map(item => <Pressable key={item.id} accessibilityRole="button" accessibilityState={{ selected: filter === item.id }}
        onPress={() => setFilter(item.id)} style={[styles.filter, { backgroundColor: filter === item.id ? colors.accent : colors.secondary }]}>
        <Copy style={{ fontSize: 13, lineHeight: 20, fontFamily: 'DMSansSemiBold', color: filter === item.id ? colors.onAccent : colors.secondaryText }}>{item.label}</Copy>
      </Pressable>)}</Row>
      <Row style={{ justifyContent: 'space-between', minHeight: 28 }}>
        <Copy style={[ui.small, { fontFamily: 'DMSansSemiBold' }]}>{search ? 'Wyniki wyszukiwania' : 'Ostatnie rozmowy'}</Copy>
        <Row style={{ gap: 6 }}>{(rooms.isFetching || action.isPending) && <ActivityIndicator size="small" color={colors.accent} />}<Copy style={ui.small}>{people.length}</Copy></Row>
      </Row>
      {(rooms.error || action.error) && <View style={{ gap: 8 }}><ErrorText error={action.error ?? rooms.error} /><Button label="Spróbuj ponownie" secondary disabled={action.isPending} onPress={rooms.error ? () => { void rooms.refetch(); } : refresh} /></View>}
    </View>
    <FlashList data={people} keyExtractor={room => room.id} renderItem={({ item }) => <ConversationRow room={item} onPress={() => open(item)} />}
      keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag" refreshing={action.isPending} onRefresh={refresh}
      ItemSeparatorComponent={() => <View style={{ height: 1, backgroundColor: colors.border, marginLeft: 76, marginRight: 16 }} />}
      contentContainerStyle={{ paddingBottom: 24 }}
      ListEmptyComponent={rooms.isPending ? <View style={{ gap: 16, padding: 16 }}>{[1, 2, 3, 4, 5].map(value => <Row key={value} style={{ minHeight: 72, gap: 12 }}>
        <View style={{ width: 48, height: 48, borderRadius: 24, backgroundColor: colors.secondary }} /><View style={{ flex: 1, gap: 10 }}><View style={{ width: '55%', height: 14, borderRadius: 8, backgroundColor: colors.secondary }} /><View style={{ width: '85%', height: 12, borderRadius: 8, backgroundColor: colors.secondary }} /></View>
      </Row>)}</View> : rooms.isError && !hasRooms ? null : <View style={styles.empty}>
        {!hasRooms && <CueMascot size={152} />}
        <Copy title style={{ fontSize: 23, lineHeight: 31, textAlign: 'center' }}>{hasRooms ? 'Nie znaleziono rozmów' : 'Twoje rozmowy, w jednym miejscu'}</Copy>
        <Copy style={[ui.body, { textAlign: 'center' }]}>{hasRooms ? 'Spróbuj innego imienia lub wybierz inny komunikator.' : 'Połącz Messenger lub WhatsApp. Potem wybierz rozmowę, a Cue pomoże Ci zrozumieć kontekst i przygotować odpowiedź.'}</Copy>
        {hasRooms ? <Button label="Pokaż wszystkie rozmowy" secondary onPress={() => { input.current?.clear(); setSearch(''); setFilter('all'); }} /> : <>
          <Button label="Połącz komunikator" onPress={() => router.push('/connections')} />
          {status?.available && <Button label={action.isPending ? 'Synchronizuję…' : 'Mam już połączone konto'} secondary disabled={action.isPending} onPress={refresh} />}
        </>}
      </View>} />
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, width: '100%', maxWidth: 600, alignSelf: 'center' },
  controls: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 12, gap: 16 },
  search: { minHeight: 52, paddingLeft: 16, paddingRight: 8, gap: 10, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderRadius: 16, borderCurve: 'continuous' },
  searchInput: { flex: 1, minHeight: 50, fontSize: 16, fontFamily: 'DMSans', paddingVertical: 10 },
  clear: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  filter: { minHeight: 44, paddingHorizontal: 14, paddingVertical: 12, borderRadius: 24, borderCurve: 'continuous', justifyContent: 'center' },
  empty: { gap: 16, padding: 24, paddingTop: 36 },
});
