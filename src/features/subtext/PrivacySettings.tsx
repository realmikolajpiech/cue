import { Alert, StyleSheet, View } from 'react-native';
import { Copy, Icon, Row } from '@/components/ui';
import { subtext, subtextCache, useSubtextAction, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { ErrorText, Page, ui } from './components';
import { useSubtextPreferences } from './preferences';
import { SettingsGroup, SettingsRow } from './SettingsRows';
import { useTranslation } from '@/i18n';

export default function PrivacySettings() {
  const { colors } = useTheme(); const { t } = useTranslation();
  const { data: status } = useSubtextStatus();
  const action = useSubtextAction();
  const selectPerson = useSubtextPreferences(s => s.selectPerson);
  function clear() {
    Alert.alert(t('privacy.clearTitle'), t('privacy.clearMessage'), [
      { text: t('common.cancel'), style: 'cancel' },
      { text: t('privacy.clearConfirm'), style: 'destructive', onPress: () => action.mutate(async () => {
        await subtext.clear();
        for (const key of ['room', 'sync', 'memory', 'reminders', 'writing-style']) subtextCache.removeQueries({ queryKey: ['subtext', key] });
        selectPerson('');
      }) },
    ]);
  }
  return <Page compact>
    <SettingsGroup title={t('privacy.onDevice')}>
      <View style={styles.explanation}>
        <Copy style={[ui.body, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>{t('privacy.onDeviceTitle')}</Copy>
        <Copy style={ui.small}>{t('privacy.onDeviceBody')}</Copy>
      </View>
    </SettingsGroup>
    <SettingsGroup title={t('privacy.ai')}>
      <View style={styles.explanation}>
        <Row style={{ gap: 8 }}><Icon name="message" size={20} color={colors.accent} /><Copy style={[ui.body, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>{!status ? t('privacy.checking') : status.cloudEnabled ? t('privacy.enabled') : t('privacy.disabled')}</Copy></Row>
        <Copy style={ui.small}>{t('privacy.aiData')}</Copy>
        <Copy style={ui.small}>{t('privacy.aiUpdates')}</Copy>
      </View>
    </SettingsGroup>
    <SettingsGroup title={t('privacy.management')} footer={t('privacy.managementFooter')}>
      <SettingsRow icon="trash" title={action.isPending ? t('privacy.deleting') : t('privacy.delete')} destructive disabled={!status?.available} busy={action.isPending} onPress={clear} />
    </SettingsGroup>
    {action.isSuccess && <Copy accessibilityLiveRegion="polite" style={ui.body}>{t('privacy.deleted')}</Copy>}
    <ErrorText error={action.error} />
  </Page>;
}

const styles = StyleSheet.create({ explanation: { padding: 16, gap: 10 } });
