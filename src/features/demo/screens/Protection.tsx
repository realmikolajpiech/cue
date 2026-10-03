import { View } from 'react-native';
import { router } from 'expo-router';
import { Screen, Copy, Row, Icon, Action } from '@/components/ui';
import { useDemo } from '@/features/demo/store';
import { ProtectionToggle } from '@/features/demo/components/ProtectionToggle';
import { ProtectionShield } from '@/features/demo/components/ProtectionShield';
import { useTheme } from '@/theme/useTheme';

export default function Protection() {
  const enabled = useDemo(s => s.enabled);
  const toggleProtection = useDemo(s => s.toggleProtection);
  const threats = useDemo(s => s.threats);
  const unread = threats.filter(t => !t.reviewed).length;
  const { colors } = useTheme();
  return <Screen>
    <View style={{ alignItems: 'center', gap: 16, paddingBottom: 8 }}>
      <ProtectionShield enabled={enabled} />
      <Copy accessibilityLiveRegion="polite" title style={{ fontSize: 28, lineHeight: 38, textAlign: 'center' }}>{enabled ? 'Ochrona jest włączona' : 'Ochrona jest wyłączona'}</Copy>
      <ProtectionToggle enabled={enabled} onToggle={toggleProtection} />
    </View>
    <View style={{ gap: 16 }}>
      <Action label="Sprawdź wiadomość" icon="message" onPress={() => router.push('/check')} />
      <Action label="Zobacz ostrzeżenia" secondary icon="warning" onPress={() => router.push('/alerts')} />
    </View>
    <Row style={{ alignItems: 'flex-start' }}>
      <Icon name={unread ? 'warning' : 'check'} size={28} color={unread ? colors.warning : colors.text} />
      <Copy style={{ flex: 1, color: colors.text }}>{unread ? `Nowe ostrzeżenia: ${unread}. Przeczytaj, co warto zrobić.` : 'Wszystkie ostrzeżenia zostały przeczytane.'}</Copy>
    </Row>
  </Screen>;
}
