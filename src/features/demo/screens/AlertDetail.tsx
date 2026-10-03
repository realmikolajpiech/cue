import { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { Screen, Copy, Card, Icon, Action } from '@/components/ui';
import { useDemo } from '@/features/demo/store';
import { threatCopy } from '@/features/demo/presentation';
import { useTheme } from '@/theme/useTheme';

export default function Detail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { threats, review } = useDemo();
  const [showWhy, setShowWhy] = useState(false);
  const { colors } = useTheme();
  const t = threats.find(t => t.id === id);
  if (!t) return <Screen header={false} back title="Nie ma tego ostrzeżenia"><Action label="Wróć do ostrzeżeń" onPress={() => router.replace('/alerts')} /></Screen>;
  const copy = threatCopy(t);
  return <Screen header={false} back>
    <View style={{ gap: 16 }}>
      <Icon name="warning" size={42} color={colors.warning} />
      <Copy title>{copy.warning}</Copy>
      <Copy>{copy.title}.</Copy>
      <Copy>{t.source} · {t.time}</Copy>
    </View>
    <Card style={{ backgroundColor: colors.warningSoft, borderColor: colors.warning }}>
      <Copy title style={{ fontSize: 26, lineHeight: 36 }}>Co zrobić teraz?</Copy>
      <Copy style={{ color: colors.text, fontFamily: 'DMSansMedium', fontSize: 22, lineHeight: 33 }}>{copy.advice}</Copy>
    </Card>
    <Action label="Rozumiem" icon="check" onPress={() => { review(t.id); if (router.canGoBack()) router.back(); else router.replace('/alerts'); }} />
    <Copy>Po naciśnięciu „Rozumiem” oznaczymy ostrzeżenie jako przeczytane. To nie oznacza, że wiadomość jest bezpieczna.</Copy>
    <Action secondary label={showWhy ? 'Ukryj wyjaśnienie' : 'Dlaczego widzę ostrzeżenie?'} onPress={() => setShowWhy(v => !v)} />
    {showWhy && <Card><Copy>W tej rozmowie pojawiły się sygnały, które mogą oznaczać oszustwo:</Copy>{t.signals.map(signal => <Copy key={signal} style={{ color: colors.text }}>• {signal}</Copy>)}<Copy>To przykład w wersji pokazowej aplikacji.</Copy></Card>}
  </Screen>;
}
