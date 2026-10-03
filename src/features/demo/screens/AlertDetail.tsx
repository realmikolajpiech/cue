import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { Screen, Copy, Card, Row, Icon, Action } from '@/components/ui';
import { useProtection } from '@/features/protection/useProtection';
import { threatCopy } from '@/features/demo/presentation';
import { useTheme } from '@/theme/useTheme';

export default function Detail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { threats, review, native } = useProtection();
  const { colors } = useTheme();
  const threat = threats.find(t => t.id === id);
  if (!threat) return <Screen header={false} back title="Nie ma tego ostrzeżenia"><Action label="Wróć do ostrzeżeń" onPress={() => router.replace('/alerts')} /></Screen>;
  const copy = native ? { warning: threat.risk, title: threat.title, advice: threat.advice, goal: threat.explanation ?? threat.title, cautions: threat.signals } : threatCopy(threat);
  return <Screen header={false} back>
    <View style={{ gap: 12 }}>
      <Icon name="warning" size={42} color={colors.warning} />
      <Copy title>{copy.warning}</Copy>
      <Copy>{copy.title}.</Copy>
      <Copy>{threat.source} · {threat.time}</Copy>
    </View>
    <Card style={{ backgroundColor: colors.warningSoft, borderColor: colors.warning }}>
      <Copy title style={{ fontSize: 26, lineHeight: 36 }}>Co zrobić teraz?</Copy>
      <Copy style={{ color: colors.text, fontFamily: 'DMSansMedium', fontSize: 22, lineHeight: 33 }}>{copy.advice}</Copy>
    </Card>
    <Card>
      <Copy title style={{ fontSize: 26, lineHeight: 36 }}>Co ktoś może próbować osiągnąć?</Copy>
      <Copy style={{ color: colors.text }}>{copy.goal}</Copy>
    </Card>
    <View style={{ gap: 20 }}>
      <Copy title style={{ fontSize: 26, lineHeight: 36 }}>Na co uważać?</Copy>
      {copy.cautions.map((caution, index) => <Row key={caution} style={{ alignItems: 'flex-start' }}>
        <Copy style={{ color: colors.warning, fontFamily: 'DMSansSemiBold' }}>{index + 1}.</Copy>
        <Copy style={{ flex: 1, color: colors.text }}>{caution}</Copy>
      </Row>)}
    </View>
    <Copy>To podejrzenie oszustwa, a nie pewność. Zanim coś zrobisz, zadzwoń do nadawcy na numer, który już znasz.</Copy>
    <Action label="Przeczytałem, rozumiem" icon="check" onPress={() => {
      void review(threat.id).then(() => {
      if (router.canGoBack()) router.back(); else router.replace('/alerts');
      });
    }} />
    <Copy>Ostrzeżenie zostanie oznaczone jako przeczytane. Ocena ryzyka pozostanie bez zmian.</Copy>
  </Screen>;
}
