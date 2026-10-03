import { View } from 'react-native';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy } from '@/components/ui';
import { CueMark, CueMascot } from '@/components/CueBrand';
import { useTheme } from '@/theme/useTheme';
import { Page, Button, ui } from './components';
import { useSubtextPreferences } from './preferences';

export default function Onboarding() {
  const insets = useSafeAreaInsets(); const finish = useSubtextPreferences(s => s.finish);
  const { colors } = useTheme();
  return <Page><View style={{ paddingTop: insets.top + 16, gap: 32 }}>
    <CueMark size={44} />
    <View style={{ backgroundColor: colors.mascotSurface, paddingVertical: 24, borderRadius: 32, borderCurve: 'continuous' }}><CueMascot size={260} /></View>
    <View style={{ gap: 12 }}>
      <Copy title style={ui.heading}>Bliżej rozmowy.</Copy>
      <Copy style={ui.title}>Wybierz osobę. Poznaj kontekst.</Copy>
      <Copy style={ui.body}>Cue pomoże Ci zrozumieć rozmowę i znaleźć słowa. Profile i podpowiedzi odpowiedzi z Messengera i WhatsAppa, w jednym miejscu.</Copy>
    </View>
    <Button label="Zaczynamy" onPress={() => { finish(); router.replace('/'); }} />
  </View></Page>;
}
