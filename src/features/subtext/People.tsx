import { View } from 'react-native';
import { Picker } from '@react-native-picker/picker';
import { router } from 'expo-router';
import { Copy } from '@/components/ui';
import { useRooms, useSubtextAction, subtext } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Page, Button, ErrorText, ui } from './components';
import { useSubtextPreferences } from './preferences';
import ProfileContent from './ProfileContent';

export default function People() {
  const rooms = useRooms(); const action = useSubtextAction(); const { colors } = useTheme();
  const selected = useSubtextPreferences(s => s.selectedPerson); const select = useSubtextPreferences(s => s.selectPerson);
  const people = rooms.data ?? [];
  const id = people.some(person => person.id === selected) ? selected : '';
  return <Page>
    <View style={{ borderWidth: 1, borderColor: colors.border, borderRadius: 12, overflow: 'hidden', backgroundColor: colors.surface }}>
      <Picker accessibilityLabel="Wybierz osobę" selectedValue={id} onValueChange={value => select(String(value))} mode="dropdown" dropdownIconColor={colors.text} style={{ color: colors.text }}>
        <Picker.Item label={rooms.isPending ? 'Wczytuję osoby…' : 'Wybierz osobę'} value="" color={colors.secondaryText} />
        {people.map(person => <Picker.Item key={person.id} value={person.id} label={`${person.name} · ${person.network === 'messenger' ? 'Messenger' : 'WhatsApp'}${person.kind === 'GROUP' ? ' · grupa' : ''}`} color={colors.text} style={{ backgroundColor: colors.surface }} />)}
      </Picker>
    </View>
    <ErrorText error={rooms.error ?? action.error} />
    {id ? <ProfileContent key={id} id={id} /> : !rooms.isPending && <View style={{ gap: 16, paddingVertical: 12 }}>
      <Copy style={ui.body}>{people.length ? 'Wybierz osobę, aby zobaczyć lub utworzyć profil.' : 'Połącz konto, aby wybrać osobę.'}</Copy>
      {!people.length && <><Button label="Połącz konto" onPress={() => router.push('/connections')} />
        <Button label={action.isPending ? 'Odświeżam…' : 'Odśwież osoby'} secondary disabled={action.isPending} onPress={() => action.mutate(subtext.refresh)} /></>}
    </View>}
  </Page>;
}
