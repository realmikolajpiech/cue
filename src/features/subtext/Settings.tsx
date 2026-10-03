import { useState } from 'react';
import { Alert } from 'react-native';
import { router } from 'expo-router';
import { useMutation } from '@tanstack/react-query';
import { usePreferences } from '@/features/preferences';
import { useDemo } from '@/features/demo/store';
import { subtext, subtextCache, useSubtextStatus, useSubtextAction } from '@/services/subtext';
import { Page, Button, ErrorText } from './components';
import { useSubtextPreferences } from './preferences';
import { SettingsDivider, SettingsGroup, SettingsRow, SettingsToggle } from './SettingsRows';

export default function Settings() {
  const statusQuery = useSubtextStatus(); const status = statusQuery.data;
  const demo = useSubtextAction();
  const ai = useMutation({ mutationFn: subtext.cloud, onSuccess: () => subtextCache.invalidateQueries({ queryKey: ['subtext', 'status'] }) });
  const dark = useDemo(s => s.dark); const toggleTheme = useDemo(s => s.toggleTheme);
  const developerMode = usePreferences(s => s.developerMode);
  const toggleDeveloperMode = usePreferences(s => s.toggleDeveloperMode);
  const [advanced, setAdvanced] = useState(false);
  const selectPerson = useSubtextPreferences(s => s.selectPerson);
  const connected = [status?.messenger.phase === 'CONNECTED' && 'Messenger', status?.whatsapp.phase === 'CONNECTED' && 'WhatsApp'].filter(Boolean);
  const phases = [status?.messenger.phase, status?.whatsapp.phase];
  const connectionLabel = statusQuery.isPending ? 'Sprawdzam połączenie…' : !status ? 'Nie udało się sprawdzić połączenia'
    : phases.some(phase => phase === 'SESSION_EXPIRED' || phase === 'REAUTH_REQUIRED') ? 'Zaloguj się ponownie'
    : phases.includes('CONNECTING') ? 'Łączę konta…'
    : connected.length ? `${connected.join(' i ')} · ${connected.length === 1 ? 'połączony' : 'połączone'}`
    : phases.includes('DISCONNECTED') ? 'Połączenie przerwane' : 'Dodaj Messenger lub WhatsApp';
  function changeAI(enabled: boolean) {
    if (!enabled) { ai.mutate(false); return; }
    Alert.alert('Włączyć analizę AI?', 'Do DeepSeek przez Supabase trafi do 80 ostatnich wiadomości, nazwy nadawców, szkic i pamięć czatu. Pamięć i lista „Warto pamiętać” aktualizują się po nowych wiadomościach wysłanych i odebranych. Wiadomości wysłane jedna po drugiej analizujemy razem. Cechy stylu liczymy na telefonie.', [
      { text: 'Anuluj', style: 'cancel' }, { text: 'Włącz', onPress: () => ai.mutate(true) },
    ]);
  }
  return <Page compact>
    {statusQuery.isError && <><ErrorText error={statusQuery.error} /><Button label="Spróbuj ponownie" secondary onPress={() => { void statusQuery.refetch(); }} /></>}
    <SettingsGroup title="Rozmowy">
      <SettingsRow icon="accounts" title="Połączone konta" subtitle={connectionLabel} onPress={() => router.push('/connections')} />
      <SettingsDivider />
      <SettingsRow icon="keyboard" title="Klawiatura Cue" subtitle="Podpowiedzi podczas pisania" onPress={() => router.push('/settings-keyboard')} />
    </SettingsGroup>
    <SettingsGroup title="Preferencje">
      <SettingsToggle icon="message" title="Analiza AI" subtitle={status && !status.available ? 'Dostępna w aplikacji na Androidzie' : 'Podsumowania i odpowiedzi'}
        value={ai.isPending ? ai.variables : status?.cloudEnabled ?? false} disabled={!status?.available} busy={ai.isPending} onValueChange={changeAI} />
      <SettingsDivider />
      <SettingsToggle icon="moon" title="Ciemny motyw" value={dark} onValueChange={toggleTheme} />
    </SettingsGroup>
    <ErrorText error={ai.error} />
    <SettingsGroup title="Pomoc i prywatność">
      <SettingsRow icon="help" title="Jak działa Cue" onPress={() => router.push('/welcome')} />
      <SettingsDivider />
      <SettingsRow icon="message" title="Rozmowa przykładowa" subtitle={demo.isPending ? 'Otwieram przykład…' : undefined} disabled={!status?.available} busy={demo.isPending}
        onPress={() => demo.mutate(async () => { const id = await subtext.demo(); selectPerson(id); router.push({ pathname: '/person/[id]', params: { id } }); })} />
      <SettingsDivider />
      <SettingsRow icon="lock" title="Dane i prywatność" onPress={() => router.push('/settings-privacy')} />
      <SettingsDivider />
      <SettingsRow icon="settings" title="Zaawansowane" expanded={advanced} onPress={() => setAdvanced(value => !value)} />
      {advanced && <><SettingsDivider /><SettingsToggle icon="code" title="Tryb deweloperski" subtitle="Pokaż narzędzia diagnostyczne" value={developerMode} onValueChange={toggleDeveloperMode} /></>}
    </SettingsGroup>
    <ErrorText error={demo.error} />
  </Page>;
}
