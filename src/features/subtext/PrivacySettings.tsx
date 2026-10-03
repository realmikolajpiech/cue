import { Alert, StyleSheet, View } from 'react-native';
import { Copy, Icon, Row } from '@/components/ui';
import { subtext, subtextCache, useSubtextAction, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { ErrorText, Page, ui } from './components';
import { useSubtextPreferences } from './preferences';
import { SettingsGroup, SettingsRow } from './SettingsRows';

export default function PrivacySettings() {
  const { colors } = useTheme();
  const { data: status } = useSubtextStatus();
  const action = useSubtextAction();
  const selectPerson = useSubtextPreferences(s => s.selectPerson);
  function clear() {
    Alert.alert('Usunąć dane zapisane w Cue?', 'Usuniesz zapisane rozmowy, podsumowania oraz pamięć i styl. Wiadomości w Messengerze i WhatsAppie pozostaną. Kolejna synchronizacja może pobrać rozmowy ponownie.', [
      { text: 'Anuluj', style: 'cancel' },
      { text: 'Usuń dane', style: 'destructive', onPress: () => action.mutate(async () => {
        await subtext.clear();
        for (const key of ['room', 'sync', 'memory', 'reminders', 'writing-style']) subtextCache.removeQueries({ queryKey: ['subtext', key] });
        selectPerson('');
      }) },
    ]);
  }
  return <Page compact>
    <SettingsGroup title="Na Twoim telefonie">
      <View style={styles.explanation}>
        <Copy style={[ui.body, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>Rozmowy, pamięć i styl</Copy>
        <Copy style={ui.small}>Cue zapisuje pobrane wiadomości i ustalenia. Cechy Twojego stylu i częste zwroty oblicza na telefonie.</Copy>
      </View>
    </SettingsGroup>
    <SettingsGroup title="Analiza AI">
      <View style={styles.explanation}>
        <Row style={{ gap: 8 }}><Icon name="message" size={20} color={colors.accent} /><Copy style={[ui.body, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>{!status ? 'Sprawdzam ustawienie…' : status.cloudEnabled ? 'Włączona' : 'Wyłączona'}</Copy></Row>
        <Copy style={ui.small}>Po włączeniu AI do DeepSeek przez Supabase trafia do 80 ostatnich wiadomości, nazwy nadawców, szkic i pamięć wybranego czatu.</Copy>
        <Copy style={ui.small}>Pamięć i lista „Warto pamiętać” aktualizują się automatycznie po nowych wiadomościach wysłanych i odebranych, po krótkiej przerwie w pisaniu. Odpowiedzi zawsze sprawdzasz i wysyłasz samodzielnie.</Copy>
      </View>
    </SettingsGroup>
    <SettingsGroup title="Zarządzanie danymi" footer="Usunięcie danych Cue nie usuwa wiadomości z komunikatorów. Synchronizacja może je pobrać ponownie.">
      <SettingsRow icon="trash" title={action.isPending ? 'Usuwam dane…' : 'Usuń dane zapisane w Cue'} destructive disabled={!status?.available} busy={action.isPending} onPress={clear} />
    </SettingsGroup>
    {action.isSuccess && <Copy accessibilityLiveRegion="polite" style={ui.body}>Dane zapisane w Cue zostały usunięte.</Copy>}
    <ErrorText error={action.error} />
  </Page>;
}

const styles = StyleSheet.create({ explanation: { padding: 16, gap: 10 } });
