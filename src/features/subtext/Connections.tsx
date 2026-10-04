import { useRef, useState, type ReactNode } from 'react';
import { ActivityIndicator, Alert, Pressable, StyleSheet, View } from 'react-native';
import { Copy, Icon, type IconName } from '@/components/ui';
import { useSubtextStatus, useSubtextAction, subtext } from '@/services/subtext';
import { type Network } from '@/types/subtext';
import { useTheme } from '@/theme/useTheme';
import { useTranslation } from '@/i18n';
import { ConversationPlatformIcon } from './ConversationRow';
import { Page, Button, Field, ErrorText, ui } from './components';
import { networkName } from './conversationPresentation';

const networks: Network[] = ['messenger', 'whatsapp', 'instagram'];

function AccountAction({ label, icon, onPress, disabled, destructive = false }: {
  label: string; icon: IconName; onPress: () => void; disabled: boolean; destructive?: boolean;
}) {
  const { colors } = useTheme();
  const color = destructive ? colors.danger : colors.text;
  return <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled }}
    disabled={disabled} onPress={onPress}
    style={({ pressed }) => [styles.action, { backgroundColor: pressed ? colors.secondary : 'transparent', opacity: disabled ? 0.5 : 1 }]}>
    <Icon name={icon} size={18} color={color} />
    <Copy style={[ui.body, { color }]}>{label}</Copy>
  </Pressable>;
}

function Account({ network, phase, available, disabled, busy, expanded, onPress, children }: {
  network: Network; phase?: string; available: boolean; disabled: boolean; busy: boolean;
  expanded: boolean; onPress: () => void; children: ReactNode;
}) {
  const { colors } = useTheme(); const { t } = useTranslation();
  const connected = phase === 'CONNECTED';
  const needsLogin = phase === 'SESSION_EXPIRED' || phase === 'REAUTH_REQUIRED';
  const label = connected ? t('connections.manageShort') : needsLogin ? t('connections.signInAgain') : t('connections.connect');
  const statusText = phase ? t(`phase.${phase}`) : t('settings.checkingConnection');
  return <View style={[styles.account, { backgroundColor: colors.surface, borderColor: colors.border }]}>
    <Pressable accessibilityRole="button" accessibilityLabel={`${networkName(network)}. ${statusText}. ${label}`}
      accessibilityState={{ disabled, busy, expanded }} disabled={disabled} onPress={onPress}
      style={({ pressed }) => [styles.accountHeader, { backgroundColor: pressed ? colors.secondary : 'transparent' }]}>
      <ConversationPlatformIcon network={network} size={40} />
      <View style={styles.accountName}>
        <Copy style={[styles.name, { color: colors.text }]}>{networkName(network)}</Copy>
        <View style={styles.status}>
          {connected && <Icon name="check" size={14} color={colors.accent} />}
          {needsLogin && <Icon name="warning" size={14} color={colors.warning} />}
          <Copy style={[styles.statusText, { color: connected ? colors.accent : needsLogin ? colors.warning : colors.secondaryText }]}>{statusText}</Copy>
        </View>
      </View>
      {busy || phase === 'CONNECTING' ? <ActivityIndicator color={colors.accent} size="small" /> : <>
        {available && <Copy style={[styles.headerAction, { color: colors.accent }]}>{label}</Copy>}
        <View style={expanded ? { transform: [{ rotate: '90deg' }] } : undefined}>
          <Icon name="chevron" size={18} color={colors.secondaryText} />
        </View>
      </>}
    </Pressable>
    {expanded && <View style={[styles.accountBody, { borderTopColor: colors.border }]}>{children}</View>}
  </View>;
}

export default function Connections() {
  const statusQuery = useSubtextStatus(); const status = statusQuery.data;
  const action = useSubtextAction(); const phone = useRef('');
  const [savedPhone, setSavedPhone] = useState('');
  const [hasPhone, setHasPhone] = useState(false);
  const [expanded, setExpanded] = useState<Network | null>(null);
  const [actionNetwork, setActionNetwork] = useState<Network | null>(null);
  const { t } = useTranslation(); const { colors } = useTheme();
  function run(network: Network, work: () => Promise<unknown>) {
    setActionNetwork(network);
    action.mutate(work);
  }
  function disconnect(network: Network) {
    Alert.alert(t('connections.disconnectTitle'), t('connections.disconnectMessage'), [
      { text: t('common.cancel'), style: 'cancel' },
      { text: t('common.disconnect'), style: 'destructive', onPress: () => run(network, () => subtext.disconnect(network)) },
    ]);
  }
  function open(network: Network) {
    if (expanded === 'whatsapp') setSavedPhone(phone.current);
    // Expand management and WhatsApp pairing in place; sign-in uses the native screen.
    if (network === 'whatsapp' || status?.[network].phase === 'CONNECTED') {
      setExpanded(value => value === network ? null : network);
      return;
    }
    setExpanded(network);
    run(network, network === 'messenger' ? subtext.messenger : subtext.instagram);
  }
  return <Page compact>
    <Copy style={ui.body}>{t('connections.intro')}</Copy>
    {status && !status.available && <Copy style={ui.small}>{t('connections.androidOnly')}</Copy>}
    {statusQuery.isError && <>
      <ErrorText error={statusQuery.error} />
      <Button label={t('common.retry')} secondary onPress={() => { void statusQuery.refetch(); }} />
    </>}
    <View style={styles.accounts}>
      {networks.map(network => {
        const connection = status?.[network];
        const connected = connection?.phase === 'CONNECTED';
        const supported = network !== 'instagram' || subtext.supportsInstagram();
        const available = !!status?.available && supported;
        const busy = action.isPending && actionNetwork === network;
        const signIn = network === 'messenger' ? subtext.messenger : subtext.instagram;
        return <Account key={network} network={network} phase={connection?.phase}
          available={available} disabled={!available || action.isPending || (network !== 'whatsapp' && connection?.phase === 'CONNECTING')}
          busy={busy} expanded={expanded === network} onPress={() => open(network)}>
          {network === 'whatsapp' && !connected ? <>
            <View style={styles.phone}>
              <Copy style={[ui.small, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>{t('connections.whatsappNumber')}</Copy>
              <Field accessibilityLabel={t('connections.whatsappNumber')} placeholder="+48 123 456 789" keyboardType="phone-pad"
                defaultValue={savedPhone} onChangeText={value => { phone.current = value; setHasPhone(!!value.trim()); }} />
              <Copy style={ui.small}>{t('connections.countryCode')}</Copy>
            </View>
            {!!status?.whatsapp.pairingCode && <View style={[styles.pairing, { backgroundColor: colors.secondary }]}>
              <Copy style={ui.small}>{t('connections.pairingCode')}</Copy>
              <Copy selectable style={[styles.code, { color: colors.text }]}>{status.whatsapp.pairingCode}</Copy>
              <Copy style={ui.small}>{t('connections.whatsappHint')}</Copy>
            </View>}
            <Button label={busy ? t('connections.preparingCode') : t('connections.getPairingCode')}
              disabled={!available || !hasPhone || action.isPending} onPress={() => run(network, () => subtext.whatsapp(phone.current))} />
          </> : <>
            {connected ? <AccountAction icon="refresh" label={busy ? t('inbox.syncing') : t('connections.refresh')}
              disabled={action.isPending} onPress={() => run(network, subtext.refresh)} /> : <>
              <Copy style={ui.small}>{t(network === 'instagram' ? 'connections.instagramHint' : 'connections.messengerHint')}</Copy>
              <Button label={t(network === 'instagram' ? 'connections.connectInstagram' : 'connections.connectMessenger')}
                disabled={!available || action.isPending || connection?.phase === 'CONNECTING'} onPress={() => run(network, signIn)} />
            </>}
            {connected && network !== 'whatsapp' && <AccountAction icon="accounts" label={t('connections.signInAgain')}
              disabled={action.isPending} onPress={() => run(network, signIn)} />}
          </>}
          {connection && connection.phase !== 'NOT_CONFIGURED' && available && <AccountAction icon="close" label={t('common.disconnect')}
            destructive disabled={action.isPending} onPress={() => disconnect(network)} />}
          {actionNetwork === network && <ErrorText error={action.error} />}
        </Account>;
      })}
    </View>
    {!!status?.available && !subtext.supportsInstagram() && <Copy style={ui.small}>{t('connections.instagramBuildRequired')}</Copy>}
    {action.isError && expanded !== actionNetwork && <ErrorText error={action.error} />}
  </Page>;
}

const styles = StyleSheet.create({
  accounts: { gap: 12, paddingTop: 4 },
  account: { borderRadius: 20, borderCurve: 'continuous', borderWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  accountHeader: { minHeight: 88, padding: 16, gap: 12, flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap' },
  accountName: { flex: 1, minWidth: 100, gap: 4 },
  name: { fontSize: 18, lineHeight: 24, fontFamily: 'DMSansSemiBold' },
  status: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  statusText: { fontSize: 12, lineHeight: 18, flexShrink: 1 },
  headerAction: { fontSize: 13, lineHeight: 20, fontFamily: 'DMSansSemiBold', maxWidth: '35%' },
  accountBody: { borderTopWidth: StyleSheet.hairlineWidth, padding: 16, gap: 12 },
  action: { minHeight: 44, paddingHorizontal: 8, paddingVertical: 8, gap: 12, flexDirection: 'row', alignItems: 'center', borderRadius: 8 },
  phone: { gap: 8 },
  pairing: { padding: 16, gap: 8, borderRadius: 16, borderCurve: 'continuous' },
  code: { fontSize: 28, lineHeight: 36, letterSpacing: 3, fontFamily: 'DMSansSemiBold', fontVariant: ['tabular-nums'] },
});
