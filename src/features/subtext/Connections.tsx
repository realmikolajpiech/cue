import { useRef } from 'react';
import { Alert, View } from 'react-native';
import { Action, Card, Copy, Icon, Row } from '@/components/ui';
import { useSubtextStatus, useSubtextAction, subtext } from '@/services/subtext';
import { type Network } from '@/types/subtext';
import { Page, Intro, Field, ErrorText, ui } from './components';

const phases: Record<string, string> = { NOT_CONFIGURED: 'Niepołączony', CONNECTING: 'Łączenie…', CONNECTED: 'Połączony', DISCONNECTED: 'Rozłączony', SESSION_EXPIRED: 'Sesja wygasła', REAUTH_REQUIRED: 'Zaloguj się ponownie' };
export default function Connections() {
  const { data: status, error } = useSubtextStatus(); const action = useSubtextAction(); const phone = useRef('');
  function disconnect(network: Network) {
    Alert.alert('Odłączyć komunikator?', 'Usuniesz sesję oraz zachowane w Subtext rozmowy i profile z tego komunikatora.', [
      { text: 'Anuluj', style: 'cancel' }, { text: 'Odłącz', style: 'destructive', onPress: () => action.mutate(() => subtext.disconnect(network)) },
    ]);
  }
  return <Page><Intro eyebrow="Na Twoim telefonie" title="Połącz rozmowy." text="Mac i dodatkowy serwer nie są potrzebne. Połączenia utrzymuje aplikacja na Androidzie." />
    {!status?.available && <Copy style={ui.body}>Zainstaluj natywny build na Androidzie, aby się zalogować. Expo Go i web służą tylko do podglądu.</Copy>}
    <Card><Row><Icon name="message" /><Copy style={ui.title}>Messenger</Copy></Row><Copy style={ui.body}>{phases[status?.messenger.phase ?? 'NOT_CONFIGURED']}</Copy>
      <Copy style={ui.body}>Zaloguj się do Facebooka. Subtext pobierze rozmowy dostępne dla połączonej sesji.</Copy>
      <Action label={status?.messenger.phase === 'CONNECTED' ? 'Odśwież rozmowy' : 'Połącz Messengera'} disabled={!status?.available || action.isPending} onPress={() => action.mutate(status?.messenger.phase === 'CONNECTED' ? subtext.refresh : subtext.messenger)} />
      {status?.messenger.phase !== 'NOT_CONFIGURED' && status?.available && <Action label="Odłącz Messengera" secondary disabled={action.isPending} onPress={() => disconnect('messenger')} />}
    </Card>
    <Card><Row><Icon name="message" /><Copy style={ui.title}>WhatsApp</Copy></Row><Copy style={ui.body}>{phases[status?.whatsapp.phase ?? 'NOT_CONFIGURED']}</Copy>
      {status?.whatsapp.phase !== 'CONNECTED' ? <>
        <Copy style={ui.body}>Podaj numer z kodem kraju. W WhatsAppie wybierz: Połączone urządzenia → Połącz urządzenie → Połącz za pomocą numeru telefonu.</Copy>
        <Field accessibilityLabel="Numer WhatsApp" placeholder="+48 123 456 789" keyboardType="phone-pad" onChangeText={value => { phone.current = value; }} />
        {status?.whatsapp.pairingCode && <Copy selectable title style={{ textAlign: 'center', letterSpacing: 4 }}>{status.whatsapp.pairingCode}</Copy>}
        <Action label={action.isPending ? 'Przygotowuję kod…' : 'Pobierz kod parowania'} disabled={!status?.available || action.isPending} onPress={() => action.mutate(() => subtext.whatsapp(phone.current))} />
      </> : <Action label="Odśwież rozmowy" disabled={action.isPending} onPress={() => action.mutate(subtext.refresh)} />}
      {status?.whatsapp.phase !== 'NOT_CONFIGURED' && status?.available && <Action label="Odłącz WhatsAppa" secondary disabled={action.isPending} onPress={() => disconnect('whatsapp')} />}
    </Card>
    <ErrorText error={action.error ?? error} />
    <View style={{ gap: 8 }}><Copy style={ui.title}>Dostępna historia</Copy><Copy style={ui.body}>Zakres historii zależy od komunikatora i sesji. Brak wiadomości w Subtext nie oznacza, że rozmowa była pusta. Po wymuszonym zatrzymaniu aplikacji otwórz ją ponownie, aby wznowić połączenia.</Copy></View>
  </Page>;
}
