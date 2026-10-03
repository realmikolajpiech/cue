import { View } from 'react-native';
import { Screen, Copy } from '@/components/ui';
import { useProtection } from '@/features/protection/useProtection';
import { ProtectionToggle } from '@/features/demo/components/ProtectionToggle';
import { ProtectionShield } from '@/features/demo/components/ProtectionShield';

export default function Protection() {
  const { active: enabled, toggleProtection, native, stateLabel, error } = useProtection();
  return <Screen centered>
    <View style={{ alignItems: 'center', gap: 16 }}>
      <ProtectionShield enabled={enabled} />
      <Copy accessibilityLiveRegion="polite" title style={{ fontSize: 28, lineHeight: 38, textAlign: 'center' }}>{enabled ? 'Ochrona jest włączona' : 'Ochrona jest wyłączona'}</Copy>
      <ProtectionToggle enabled={enabled} onToggle={toggleProtection} />
      {native && !enabled && <Copy style={{ textAlign: 'center' }}>{stateLabel}</Copy>}
      {error && <Copy accessibilityLiveRegion="polite">{error}</Copy>}
    </View>
  </Screen>;
}
