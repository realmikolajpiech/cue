import { View } from 'react-native';
import { Copy } from '@/components/ui';
import { subtext, useSubtextAction, useSubtextStatus } from '@/services/subtext';
import { ErrorText, Field, Page, ui } from './components';
import { SettingsDivider, SettingsGroup, SettingsRow } from './SettingsRows';
import { useTranslation } from '@/i18n';

export default function KeyboardSettings() {
  const { data: status } = useSubtextStatus();
  const action = useSubtextAction();
  const { t } = useTranslation();
  return <Page compact>
    <Copy style={ui.body}>{t('keyboard.intro')}</Copy>
    <SettingsGroup title={t('keyboard.setup')}>
      <SettingsRow icon="settings" title={t('keyboard.enable')} subtitle={t('keyboard.enableSubtitle')} disabled={!status?.available || action.isPending}
        onPress={() => action.mutate(subtext.keyboardSettings)} />
      <SettingsDivider />
      <SettingsRow icon="keyboard" title={t('keyboard.choose')} subtitle={t('keyboard.chooseSubtitle')} disabled={!status?.available || action.isPending}
        onPress={() => action.mutate(async () => subtext.keyboard())} />
    </SettingsGroup>
    {status && !status.available && <Copy style={ui.small}>{t('keyboard.androidOnly')}</Copy>}
    <ErrorText error={action.error} />
    <View style={{ gap: 8 }}>
      <Copy style={ui.title}>{t('keyboard.try')}</Copy>
      <Field accessibilityLabel={t('keyboard.try')} placeholder={t('keyboard.tryPlaceholder')} multiline style={{ minHeight: 112, textAlignVertical: 'top' }} />
      <Copy style={ui.small}>{t('keyboard.notSent')}</Copy>
    </View>
  </Page>;
}
