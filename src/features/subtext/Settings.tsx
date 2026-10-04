import { useState } from 'react';
import { router } from 'expo-router';
import { useMutation } from '@tanstack/react-query';
import { usePreferences } from '@/features/preferences';
import { useAppearance } from '@/theme/preferences';
import { subtext, subtextCache, useSubtextStatus, useSubtextAction } from '@/services/subtext';
import { Page, Button, ErrorText } from './components';
import { useSubtextPreferences } from './preferences';
import { SettingsDivider, SettingsGroup, SettingsRow, SettingsToggle } from './SettingsRows';
import { LanguagePicker } from '@/components/LanguagePicker';
import { plural, useTranslation } from '@/i18n';

export default function Settings() {
  const statusQuery = useSubtextStatus(); const status = statusQuery.data;
  const { t } = useTranslation();
  const demo = useSubtextAction();
  const ai = useMutation({ mutationFn: subtext.cloud, onSuccess: () => subtextCache.invalidateQueries({ queryKey: ['subtext', 'status'] }) });
  const dark = useAppearance(s => s.dark); const toggleTheme = useAppearance(s => s.toggleTheme);
  const developerMode = usePreferences(s => s.developerMode);
  const toggleDeveloperMode = usePreferences(s => s.toggleDeveloperMode);
  const demoMode = usePreferences(s => s.demoMode); const toggleDemoMode = usePreferences(s => s.toggleDemoMode);
  const [advanced, setAdvanced] = useState(false);
  const selectPerson = useSubtextPreferences(s => s.selectPerson);
  const connected = [status?.messenger.phase === 'CONNECTED' && 'Messenger', status?.whatsapp.phase === 'CONNECTED' && 'WhatsApp', status?.instagram.phase === 'CONNECTED' && 'Instagram'].filter(Boolean);
  const phases = [status?.messenger.phase, status?.whatsapp.phase, status?.instagram.phase];
  const connectionLabel = statusQuery.isPending ? t('settings.checkingConnection') : !status ? t('settings.connectionCheckFailed')
    : phases.some(phase => phase === 'SESSION_EXPIRED' || phase === 'REAUTH_REQUIRED') ? t('settings.signInAgain')
    : phases.includes('CONNECTING') ? t('settings.connectingAccounts')
    : connected.length ? plural('settings.connected', connected.length, { networks: connected.join(t('common.listSeparator')) })
    : phases.includes('DISCONNECTED') ? t('settings.connectionInterrupted') : t('settings.addAccount');
  return <Page compact>
    {statusQuery.isError && <><ErrorText error={statusQuery.error} /><Button label={t('common.retry')} secondary onPress={() => { void statusQuery.refetch(); }} /></>}
    <SettingsGroup title={t('settings.conversations')}>
      <SettingsRow icon="accounts" title={t('settings.connectedAccounts')} subtitle={connectionLabel} onPress={() => router.push('/connections')} />
      <SettingsDivider />
      <SettingsRow icon="keyboard" title={t('settings.keyboard')} subtitle={t('settings.keyboardSubtitle')} onPress={() => router.push('/settings-keyboard')} />
    </SettingsGroup>
    <SettingsGroup title={t('settings.preferences')}>
      <SettingsToggle icon="message" title={t('settings.ai')} subtitle={status && !status.available ? t('settings.aiAndroidOnly') : t('settings.aiSubtitle')}
        value={ai.isPending ? ai.variables : status?.cloudEnabled ?? false} disabled={!status?.available} busy={ai.isPending} onValueChange={enabled => ai.mutate(enabled)} />
      <SettingsDivider />
      <SettingsToggle icon="moon" title={t('settings.darkTheme')} value={dark} onValueChange={toggleTheme} />
      <SettingsDivider />
      <LanguagePicker />
    </SettingsGroup>
    <ErrorText error={ai.error} />
    <SettingsGroup title={t('settings.helpPrivacy')}>
      <SettingsRow icon="help" title={t('settings.howItWorks')} onPress={() => router.push('/welcome')} />
      <SettingsDivider />
      {!demoMode && <><SettingsRow icon="message" title={t('common.exampleConversation')} subtitle={demo.isPending ? t('settings.openingExample') : undefined} disabled={!status?.available} busy={demo.isPending}
        onPress={() => demo.mutate(async () => { const id = await subtext.demo(); selectPerson(id); router.push({ pathname: '/person/[id]', params: { id } }); })} />
      <SettingsDivider />
      </>}
      <SettingsToggle icon="accounts" title={t('settings.demoMode')} subtitle={t('settings.demoModeSubtitle')} value={demoMode} onValueChange={toggleDemoMode} />
      <SettingsDivider />
      <SettingsRow icon="lock" title={t('settings.privacy')} onPress={() => router.push('/settings-privacy')} />
      <SettingsDivider />
      <SettingsRow icon="settings" title={t('settings.advanced')} expanded={advanced} onPress={() => setAdvanced(value => !value)} />
      {advanced && <><SettingsDivider /><SettingsToggle icon="code" title={t('settings.developerMode')} subtitle={t('settings.developerModeSubtitle')} value={developerMode} onValueChange={toggleDeveloperMode} /></>}
    </SettingsGroup>
    <ErrorText error={demo.error} />
  </Page>;
}
