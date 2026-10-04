import { useRef } from 'react';
import { Alert } from 'react-native';
import { Card, Copy, Icon, Row } from '@/components/ui';
import { useSubtextStatus, useSubtextAction, subtext } from '@/services/subtext';
import { type Network } from '@/types/subtext';
import { useTranslation } from '@/i18n';
import { Page, Button, Disclosure, Field, ErrorText, ui } from './components';

export default function Connections() {
  const { data: status, error } = useSubtextStatus(); const action = useSubtextAction(); const phone = useRef('');
  const { t } = useTranslation();
  const phase = (value?: string) => t(`phase.${value ?? 'NOT_CONFIGURED'}`);
  function disconnect(network: Network) {
    Alert.alert(t('connections.disconnectTitle'), t('connections.disconnectMessage'), [
      { text: t('common.cancel'), style: 'cancel' }, { text: t('common.disconnect'), style: 'destructive', onPress: () => action.mutate(() => subtext.disconnect(network)) },
    ]);
  }
  return <Page>
    <Copy style={ui.body}>{t('connections.intro')}</Copy>
    {status && !status.available && <Copy style={ui.body}>{t('connections.androidOnly')}</Copy>}
    <Card><Row><Icon name="message" /><Copy style={ui.title}>Messenger</Copy></Row><Copy style={ui.body}>{phase(status?.messenger.phase)}</Copy>
      <Button label={status?.messenger.phase === 'CONNECTED' ? t('connections.refresh') : t('connections.connectMessenger')} disabled={!status?.available || action.isPending} onPress={() => action.mutate(status?.messenger.phase === 'CONNECTED' ? subtext.refresh : subtext.messenger)} />
      {status?.messenger.phase !== 'NOT_CONFIGURED' && status?.available && <Disclosure label={t('connections.manage')}>
        <Button label={t('connections.signInAgain')} secondary disabled={action.isPending} onPress={() => action.mutate(subtext.messenger)} />
        <Button label={t('common.disconnect')} secondary disabled={action.isPending} onPress={() => disconnect('messenger')} />
      </Disclosure>}
    </Card>
    <Card><Row><Icon name="message" /><Copy style={ui.title}>WhatsApp</Copy></Row><Copy style={ui.body}>{phase(status?.whatsapp.phase)}</Copy>
      {status?.whatsapp.phase !== 'CONNECTED' ? <>
        <Copy style={ui.body}>{t('connections.whatsappHint')}</Copy>
        <Field accessibilityLabel={t('connections.whatsappNumber')} placeholder="+48 123 456 789" keyboardType="phone-pad" onChangeText={value => { phone.current = value; }} />
        {status?.whatsapp.pairingCode && <Copy selectable title style={{ textAlign: 'center', letterSpacing: 4 }}>{status.whatsapp.pairingCode}</Copy>}
        <Button label={action.isPending ? t('connections.preparingCode') : t('connections.getPairingCode')} disabled={!status?.available || action.isPending} onPress={() => action.mutate(() => subtext.whatsapp(phone.current))} />
      </> : <Button label={t('connections.refresh')} disabled={action.isPending} onPress={() => action.mutate(subtext.refresh)} />}
      {status?.whatsapp.phase !== 'NOT_CONFIGURED' && status?.available && <Disclosure label={t('connections.manage')}><Button label={t('common.disconnect')} secondary disabled={action.isPending} onPress={() => disconnect('whatsapp')} /></Disclosure>}
    </Card>
    <ErrorText error={action.error ?? error} />

  </Page>;
}
