import { useMemo, useState } from 'react';
import { Pressable, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { Action, Copy, Icon, Row } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { useRooms, useSubtextStatus, useSubtextAction, subtext } from '@/services/subtext';
import { Intro, Field, ErrorText, ui } from './components';

export default function People() {
  const { colors } = useTheme(); const rooms = useRooms(); const status = useSubtextStatus(); const action = useSubtextAction();
  const [search, setSearch] = useState('');
  const filtered = useMemo(() => (rooms.data ?? []).filter(room => room.name.toLocaleLowerCase().includes(search.toLocaleLowerCase())), [rooms.data, search]);
  return <View style={{ flex: 1, backgroundColor: colors.background }}>
    <FlashList data={filtered} keyExtractor={item => item.id} contentContainerStyle={{ padding: 24, paddingBottom: 40 }}
      refreshing={action.isPending} onRefresh={() => action.mutate(subtext.refresh)}
      ListHeaderComponent={<View style={{ gap: 20, marginBottom: 24 }}>
        <Intro eyebrow="Pamięć relacji" title="Zanim odpowiesz." text="Ludzie, rozmowy i rzeczy, które warto pamiętać." />
        <Field accessibilityLabel="Szukaj rozmowy" placeholder="Szukaj osoby lub rozmowy" onChangeText={setSearch} />
        {!status.data?.available && <Copy style={ui.small}>Połączenia wymagają buildu Subtext na Androidzie. Ten podgląd nie zawiera prywatnych rozmów.</Copy>}
        <ErrorText error={rooms.error ?? action.error} />
      </View>}
      ListEmptyComponent={<View style={{ paddingVertical: 32, gap: 16 }}><Icon name="message" size={36} />
        <Copy style={ui.title}>{rooms.isPending ? 'Wczytuję rozmowy…' : search ? 'Nie znaleziono rozmowy' : 'Poznaj kontekst swoich rozmów'}</Copy>
        <Copy style={ui.body}>{search ? 'Spróbuj innej nazwy.' : 'Połącz pierwszy komunikator. Dostępne rozmowy pojawią się tutaj po synchronizacji.'}</Copy>
        {!search && <Action label="Połącz komunikator" onPress={() => router.navigate('/check')} />}
        {!search && status.data?.available && <Action label="Wypróbuj przykładową rozmowę" secondary disabled={action.isPending} onPress={() => action.mutate(async () => {
          const id = await subtext.demo(); router.push({ pathname: '/person/[id]', params: { id } });
        })} />}
      </View>}
      renderItem={({ item }) => <Pressable accessibilityRole="button" accessibilityLabel={`Otwórz rozmowę: ${item.name}`}
        onPress={() => router.push({ pathname: '/person/[id]', params: { id: item.id } })}
        style={({ pressed }) => ({ paddingVertical: 20, borderBottomWidth: 1, borderColor: colors.border, backgroundColor: pressed ? colors.secondary : colors.background })}>
        <Row style={{ alignItems: 'flex-start' }}>
          <View style={{ width: 48, height: 48, borderRadius: 16, borderCurve: 'continuous', backgroundColor: colors.secondary, alignItems: 'center', justifyContent: 'center' }}><Copy title style={{ fontSize: 22 }}>{item.name.slice(0, 1).toUpperCase()}</Copy></View>
          <View style={{ flex: 1, gap: 4 }}><Copy style={[ui.title, { color: colors.text }]} numberOfLines={1}>{item.name}</Copy>
            <Copy style={ui.small}>{item.network === 'messenger' ? 'Messenger' : 'WhatsApp'}{item.kind === 'GROUP' ? ' · Grupa' : ''}{item.profile ? ' · Profil gotowy' : ''}</Copy>
            <Copy style={ui.body} numberOfLines={2}>{item.snippet || 'Otwórz, aby pobrać dostępne wiadomości.'}</Copy>
          </View><Icon name="chevron" size={18} />
        </Row>
      </Pressable>} />
  </View>;
}
