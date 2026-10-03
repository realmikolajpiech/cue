import { useRef } from 'react';
import { Alert } from 'react-native';
import { Card, Copy, Icon, Row } from '@/components/ui';
import { useSubtextStatus, useSubtextAction, subtext } from '@/services/subtext';
import { type Network } from '@/types/subtext';
import { Page, Button, Disclosure, Field, ErrorText, ui } from './components';

const phases: Record<string, string> = { NOT_CONFIGURED: 'Niepołączony', CONNECTING: 'Łączenie…', CONNECTED: 'Połączony', DISCONNECTED: 'Rozłączony', SESSION_EXPIRED: 'Sesja wygasła', REAUTH_REQUIRED: 'Zaloguj się ponownie' };
export default function Connections() {
  const { data: status, error } = useSubtextStatus(); const action = useSubtextAction(); const phone = useRef('');
  function disconnect(network: Network) {
    Alert.alert('Odłączyć komunikator?', 'Usuniesz sesję oraz zachowane w Cue rozmowy i profile z tego komunikatora.', [
      { text: 'Anuluj', style: 'cancel' }, { text: 'Odłącz', style: 'destructive', onPress: () => action.mutate(() => subtext.disconnect(network)) },
    ]);
  }
  return <Page>
    {!status?.available && <Copy style={ui.body}>Połączenie kont jest dostępne w aplikacji na Androidzie.</Copy>}
    <Card><Row><Icon name="message" /><Copy style={ui.title}>Messenger</Copy></Row><Copy style={ui.body}>{phases[status?.messenger.phase ?? 'NOT_CONFIGURED']}</Copy>
      <Button label={status?.messenger.phase === 'CONNECTED' ? 'Odśwież rozmowy' : 'Połącz Messengera'} disabled={!status?.available || action.isPending} onPress={() => action.mutate(status?.messenger.phase === 'CONNECTED' ? subtext.refresh : subtext.messenger)} />
      {status?.messenger.phase !== 'NOT_CONFIGURED' && status?.available && <Disclosure label="Zarządzaj kontem"><Button label="Odłącz" secondary disabled={action.isPending} onPress={() => disconnect('messenger')} /></Disclosure>}
    </Card>
    <Card><Row><Icon name="message" /><Copy style={ui.title}>WhatsApp</Copy></Row><Copy style={ui.body}>{phases[status?.whatsapp.phase ?? 'NOT_CONFIGURED']}</Copy>
      {status?.whatsapp.phase !== 'CONNECTED' ? <>
        <Copy style={ui.body}>Kod wpisz w WhatsApp → Połączone urządzenia → Połącz za pomocą numeru.</Copy>
        <Field accessibilityLabel="Numer WhatsApp" placeholder="+48 123 456 789" keyboardType="phone-pad" onChangeText={value => { phone.current = value; }} />
        {status?.whatsapp.pairingCode && <Copy selectable title style={{ textAlign: 'center', letterSpacing: 4 }}>{status.whatsapp.pairingCode}</Copy>}
        <Button label={action.isPending ? 'Przygotowuję kod…' : 'Pobierz kod parowania'} disabled={!status?.available || action.isPending} onPress={() => action.mutate(() => subtext.whatsapp(phone.current))} />
      </> : <Button label="Odśwież rozmowy" disabled={action.isPending} onPress={() => action.mutate(subtext.refresh)} />}
      {status?.whatsapp.phase !== 'NOT_CONFIGURED' && status?.available && <Disclosure label="Zarządzaj kontem"><Button label="Odłącz" secondary disabled={action.isPending} onPress={() => disconnect('whatsapp')} /></Disclosure>}
    </Card>
    <ErrorText error={action.error ?? error} />

  </Page>;
}
