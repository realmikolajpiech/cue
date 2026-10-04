import { Platform, View } from 'react-native';
import { Copy } from '@/components/ui';
import { subtext, useSubtextAction, useSubtextStatus } from '@/services/subtext';
import { ErrorText, Field, Page, ui } from './components';
import { SettingsDivider, SettingsGroup, SettingsRow } from './SettingsRows';
import { useTranslation } from '@/i18n';

export default function KeyboardSettings() {
  const { t } = useTranslation();
  return <Page compact>
    <Copy style={ui.body}>{t('keyboard.intro')}</Copy>
    <KeyboardSetup />
  </Page>;
}

export function KeyboardSetup() {
  const { data: status } = useSubtextStatus();
  const action = useSubtextAction();
  const ios = Platform.OS === 'ios';
  const { t } = useTranslation();
  return <>
    <SettingsGroup title={t('keyboard.setup')}>
      <SettingsRow icon="settings" title={t('keyboard.enable')} subtitle={ios ? t('keyboard.enableSubtitleIOS') : t('keyboard.enableSubtitle')} disabled={!status?.available || action.isPending}
        onPress={() => action.mutate(subtext.keyboardSettings)} />
      <SettingsDivider />
      <SettingsRow icon="keyboard" title={t('keyboard.choose')} subtitle={ios ? t('keyboard.chooseSubtitleIOS') : t('keyboard.chooseSubtitle')} disabled={!status?.available || action.isPending}
        onPress={() => action.mutate(async () => subtext.keyboard())} />
    </SettingsGroup>
    {status && !status.available && <Copy style={ui.small}>{t('keyboard.androidOnly')}</Copy>}
    {ios && <Copy style={ui.small}>{t('keyboard.fullAccessIOS')}</Copy>}
    <ErrorText error={action.error} />
    <View style={{ gap: 8 }}>
      <Copy style={ui.title}>{t('keyboard.try')}</Copy>
      <Field accessibilityLabel={t('keyboard.try')} placeholder={t('keyboard.tryPlaceholder')} multiline style={{ minHeight: 112, textAlignVertical: 'top' }} />
      <Copy style={ui.small}>{t('keyboard.notSent')}</Copy>
    </View>
  </>;
}
