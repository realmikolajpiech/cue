import { t, useLanguage } from '@/i18n/legacy';
import { View } from 'react-native';
import { router } from 'expo-router';
import { Screen, Copy, Action } from '@/components/ui';
import { usePreferences } from '@/features/preferences';
import { useProtection } from '@/features/protection/useProtection';
import { ProtectionToggle } from '@/features/demo/components/ProtectionToggle';
import { ProtectionShield } from '@/features/demo/components/ProtectionShield';

export default function Protection() {
  useLanguage();
  const { active: enabled, toggleProtection, native, stateLabel, error, busy, status } = useProtection();
  const restartOnboarding = usePreferences(s => s.restartOnboarding);
  return <Screen centered>
    <View style={{ alignItems: 'center', gap: 16 }}>
      <View style={{ alignItems: 'center', gap: 8, alignSelf: 'stretch' }}>
        <ProtectionShield enabled={enabled} />
        <Copy accessibilityLiveRegion="polite" title style={{ fontSize: 28, lineHeight: 38, textAlign: 'center' }}>{enabled ? t('Ochrona włączona') : t('Ochrona wyłączona')}</Copy>
      </View>
      <ProtectionToggle enabled={enabled} disabled={busy || status?.modelState === 'loading'} onToggle={toggleProtection} />
      {native && !enabled && <Copy style={{ textAlign: 'center' }}>{stateLabel}</Copy>}
      {error && <Copy accessibilityLiveRegion="polite">{error}</Copy>}
      {native && status?.available && (!status.modelInstalled || !status.notificationAccess) && <Action label={t("Dokończ przygotowanie ochrony")} secondary onPress={() => { restartOnboarding(); router.push('/onboarding'); }} />}
    </View>
  </Screen>;
}
