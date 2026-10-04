import { useDeferredValue, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import Animated, { useAnimatedScrollHandler, useAnimatedStyle, useSharedValue } from 'react-native-reanimated';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy, Icon, Row } from '@/components/ui';
import { CueMascot } from '@/components/CueBrand';
import { subtext, subtextCache, useRooms, useSubtextAction, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, ui } from './components';
import { ConversationPlatformIcon, ConversationRow } from './ConversationRow';
import { filterConversations, type InboxFilter } from './conversationPresentation';
import { useSubtextPreferences } from './preferences';
import type { Room } from '@/types/subtext';
import { useTranslation } from '@/i18n';

const filters = [{ id: 'all', label: 'inbox.all' }, { id: 'messenger', label: 'Messenger' }, { id: 'whatsapp', label: 'WhatsApp' }, { id: 'instagram', label: 'Instagram' }] as const;

type InboxItem = { type: 'intro' | 'controls' | 'section' | 'empty' } | { type: 'room'; room: Room };
const AnimatedInboxList = Animated.createAnimatedComponent(FlashList<InboxItem>);

function InboxControls({ search, filter, setSearch, setFilter }: {
  search: string; filter: InboxFilter; setSearch: (text: string) => void; setFilter: (filter: InboxFilter) => void;
}) {
  const { colors } = useTheme(); const { t } = useTranslation();
  const input = useRef<TextInput>(null);
  return <View style={[styles.controls, { backgroundColor: colors.background }]}>
      <View style={[styles.search, { backgroundColor: colors.surface }]}>
        <Icon name="search" size={20} color={colors.secondaryText} />
        <TextInput ref={input} value={search} accessibilityLabel={t('inbox.searchLabel')} placeholder={t('inbox.searchPlaceholder')} placeholderTextColor={colors.secondaryText} cursorColor={colors.accent} selectionColor={colors.accent}
          onChangeText={setSearch} autoCorrect={false} autoCapitalize="none" returnKeyType="search" onSubmitEditing={() => input.current?.blur()}
          style={[styles.searchInput, { color: colors.text }]} />
        {!!search && <Pressable accessibilityRole="button" accessibilityLabel={t('inbox.clearSearch')} onPress={() => { input.current?.clear(); setSearch(''); }} style={styles.clear}><Icon name="close" size={18} color={colors.secondaryText} /></Pressable>}
      </View>
      <Row style={{ gap: 6, flexWrap: 'wrap' }}>{filters.map(item => <Pressable key={item.id} accessibilityRole="button" accessibilityState={{ selected: filter === item.id }}
        onPress={() => setFilter(item.id)} style={({ pressed }) => [styles.filter, { opacity: pressed ? .6 : 1 }]}>
        <View style={[styles.filterPill, { backgroundColor: filter === item.id ? colors.secondary : 'transparent', borderColor: filter === item.id ? colors.secondary : colors.border }]}>
          {item.id === 'all' ? <Icon name="message" size={14} color={filter === item.id ? colors.accent : colors.secondaryText} /> : <ConversationPlatformIcon network={item.id} size={16} />}
          <Copy numberOfLines={1} adjustsFontSizeToFit minimumFontScale={.85} style={{ flexShrink: 1, fontSize: 12, lineHeight: 18, fontFamily: 'DMSansSemiBold', color: filter === item.id ? colors.accent : colors.secondaryText }}>{item.id === 'all' ? t(item.label) : item.label}</Copy>
        </View>
      </Pressable>)}</Row>
    </View>;
}

export default function People() {
  const insets = useSafeAreaInsets();
  const rooms = useRooms(); const action = useSubtextAction(); const { data: status } = useSubtextStatus(); const { colors } = useTheme(); const { t } = useTranslation();
  const [search, setSearch] = useState(''); const deferredSearch = useDeferredValue(search);
  const [filter, setFilter] = useState<InboxFilter>('all');
  const [viewportHeight, setViewportHeight] = useState(0);
  const [introHeight, setIntroHeight] = useState(0);
  const [controlsHeight, setControlsHeight] = useState(0);
  const scrollOffset = useSharedValue(0);
  const introOffset = useSharedValue(0);
  const onScroll = useAnimatedScrollHandler(event => {
    scrollOffset.set(event.contentOffset.y);
  });
  // One persistent bar follows the finger and stops at the safe-area edge.
  // No JS threshold, duplicated input, or mounting handoff at the pin boundary.
  const controlsPosition = useAnimatedStyle(() => ({
    transform: [{ translateY: Math.max(0, introOffset.get() - scrollOffset.get()) }],
  }));
  const select = useSubtextPreferences(s => s.selectPerson);
  const people = useMemo(() => filterConversations(rooms.data ?? [], deferredSearch, filter), [rooms.data, deferredSearch, filter]);
  const hasRooms = !!rooms.data?.length;
  const items = useMemo<InboxItem[]>(() => [
    { type: 'intro' }, { type: 'controls' }, { type: 'section' },
    ...(people.length ? people.map(room => ({ type: 'room' as const, room })) : [{ type: 'empty' as const }]),
  ], [people]);
  function open(room: Room) {
    Keyboard.dismiss();
    select(room.id);
    if (!subtextCache.getQueryData(['subtext', 'room', room.id])) subtextCache.setQueryData(['subtext', 'room', room.id], room);
    router.push({ pathname: '/person/[id]', params: { id: room.id } });
  }
  function refresh() { action.mutate(subtext.refresh); }
  return <View onLayout={event => setViewportHeight(event.nativeEvent.layout.height - insets.top)} style={[styles.screen, { backgroundColor: colors.background, paddingTop: insets.top }]}>
    <AnimatedInboxList data={items} keyExtractor={item => item.type === 'room' ? item.room.id : item.type}
      getItemType={item => item.type} onScroll={onScroll} scrollEventThrottle={16} maintainVisibleContentPosition={{ disabled: true }}
      keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag" refreshing={action.isPending} onRefresh={refresh}
      // Keep short search results tall enough for the active search field to stay pinned.
      contentContainerStyle={{ paddingBottom: 24, minHeight: viewportHeight + introHeight }}
      renderItem={({ item, index }) => {
        if (item.type === 'intro') return <View style={styles.intro} onLayout={event => { const height = event.nativeEvent.layout.height; introOffset.set(height); setIntroHeight(height); }}>
        <Row style={{ justifyContent: 'space-between' }}>
          <Row style={{ gap: 10 }}><CueMascot pose="wave" size={32} /><Copy title accessibilityRole="header" style={styles.heading}>{t('inbox.title')}</Copy></Row>
          <Pressable accessibilityRole="button" accessibilityLabel={t('nav.connectedAccounts')} onPress={() => router.push('/connections')}
            style={({ pressed }) => ({ width: 44, height: 44, alignItems: 'center', justifyContent: 'center', opacity: pressed ? 0.5 : 1 })}>
            <Icon name="accounts" size={24} color={colors.accent} />
          </Pressable>
        </Row>
        </View>;
        if (item.type === 'controls') return <View style={{ height: controlsHeight }} />;
        if (item.type === 'section') return <View style={styles.listHeader}>
      <Row style={{ justifyContent: 'space-between', minHeight: 28 }}>
        <Copy style={[ui.small, { fontFamily: 'DMSansSemiBold' }]}>{search ? t('inbox.results') : t('inbox.recent')}</Copy>
        <Row style={{ gap: 6 }}>{(rooms.isFetching || action.isPending) && <ActivityIndicator size="small" color={colors.accent} />}<Copy style={ui.small}>{people.length}</Copy></Row>
      </Row>
      {(rooms.error || action.error) && <View style={{ gap: 8 }}><ErrorText error={action.error ?? rooms.error} /><Button label={t('common.retry')} secondary disabled={action.isPending} onPress={rooms.error ? () => { void rooms.refetch(); } : refresh} /></View>}
        </View>;
        if (item.type === 'empty') return rooms.isPending ? <View style={{ gap: 16, padding: 16 }}>{[1, 2, 3, 4, 5].map(value => <Row key={value} style={{ minHeight: 72, gap: 12 }}>
        <View style={{ width: 48, height: 48, borderRadius: 24, backgroundColor: colors.secondary }} /><View style={{ flex: 1, gap: 10 }}><View style={{ width: '55%', height: 14, borderRadius: 8, backgroundColor: colors.secondary }} /><View style={{ width: '85%', height: 12, borderRadius: 8, backgroundColor: colors.secondary }} /></View>
      </Row>)}</View> : rooms.isError && !hasRooms ? null : <View style={styles.empty}>
        <Copy title style={{ fontSize: 23, lineHeight: 31, textAlign: 'center' }}>{hasRooms ? t('inbox.notFound') : t('inbox.addFirst')}</Copy>
        <Copy style={[ui.body, { textAlign: 'center' }]}>{hasRooms ? t('inbox.notFoundHint') : t('inbox.addFirstHint')}</Copy>
        {hasRooms ? <Button label={t('inbox.showAll')} secondary onPress={() => { setSearch(''); setFilter('all'); }} /> : <>
          <Button label={t('inbox.connect')} onPress={() => router.push('/connections')} />
          {status?.available && <Button label={action.isPending ? t('inbox.syncing') : t('inbox.alreadyConnected')} secondary disabled={action.isPending} onPress={refresh} />}
        </>}
      </View>;
        if (item.type === 'room') return <View><ConversationRow room={item.room} onPress={() => open(item.room)} />
          {index < items.length - 1 && <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: colors.border, marginLeft: 80, marginRight: 20 }} />}
        </View>;
        return null;
      }} />
    <Animated.View onLayout={event => setControlsHeight(event.nativeEvent.layout.height)}
      style={[styles.controlsOverlay, { top: insets.top, opacity: introHeight ? 1 : 0 }, controlsPosition]}>
      <InboxControls search={search} filter={filter} setSearch={setSearch} setFilter={setFilter} />
    </Animated.View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, width: '100%', maxWidth: 600, alignSelf: 'center' },
  controlsOverlay: { position: 'absolute', left: 0, right: 0, zIndex: 1 },
  controls: { paddingHorizontal: 20, paddingTop: 4, paddingBottom: 8, gap: 8 },
  intro: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 12, gap: 16 },
  heading: { fontSize: 26, lineHeight: 34, letterSpacing: -.6 },
  listHeader: { paddingHorizontal: 20, paddingTop: 4, paddingBottom: 4, gap: 12 },
  search: { minHeight: 44, paddingLeft: 12, paddingRight: 4, gap: 8, flexDirection: 'row', alignItems: 'center', borderRadius: 12, borderCurve: 'continuous' },
  searchInput: { flex: 1, minHeight: 44, fontSize: 15, fontFamily: 'DMSans', paddingVertical: 8 },
  clear: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  filter: { flexBasis: '45%', flexGrow: 1, minWidth: 0, minHeight: 44, justifyContent: 'center', paddingVertical: 6 },
  filterPill: { minHeight: 32, paddingHorizontal: 8, paddingVertical: 6, gap: 6, flexDirection: 'row', alignItems: 'center', borderWidth: StyleSheet.hairlineWidth, borderRadius: 16, borderCurve: 'continuous', justifyContent: 'center' },
  empty: { gap: 16, padding: 24, paddingTop: 36 },
});
