import { View } from 'react-native';
import { Copy } from '@/components/ui';
import { subtext, useSubtextAction, useSubtextStatus } from '@/services/subtext';
import { ErrorText, Field, Page, ui } from './components';
import { SettingsDivider, SettingsGroup, SettingsRow } from './SettingsRows';

export default function KeyboardSettings() {
  const { data: status } = useSubtextStatus();
  const action = useSubtextAction();
  return <Page compact>
    <Copy style={ui.body}>Korzystaj z podpowiedzi Cue bez wychodzenia z komunikatora.</Copy>
    <SettingsGroup title="Konfiguracja">
      <SettingsRow icon="settings" title="1. Włącz klawiaturę Cue" subtitle="Otwórz listę klawiatur Androida" disabled={!status?.available || action.isPending}
        onPress={() => action.mutate(subtext.keyboardSettings)} />
      <SettingsDivider />
      <SettingsRow icon="keyboard" title="2. Wybierz Cue do pisania" subtitle="Wybierz ją z klawiatur telefonu" disabled={!status?.available || action.isPending}
        onPress={() => action.mutate(async () => subtext.keyboard())} />
    </SettingsGroup>
    {status && !status.available && <Copy style={ui.small}>Klawiatura Cue jest dostępna w aplikacji na Androidzie.</Copy>}
    <ErrorText error={action.error} />
    <View style={{ gap: 8 }}>
      <Copy style={ui.title}>Wypróbuj klawiaturę</Copy>
      <Field accessibilityLabel="Wypróbuj klawiaturę" placeholder="Napisz kilka słów…" multiline style={{ minHeight: 112, textAlignVertical: 'top' }} />
      <Copy style={ui.small}>Tekst z tego pola nie jest wysyłany jako wiadomość.</Copy>
    </View>
  </Page>;
}
