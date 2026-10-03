import { View } from 'react-native';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy } from '@/components/ui';
import { Page, Button, ui } from './components';
import { useSubtextPreferences } from './preferences';

export default function Onboarding() {
  const insets = useSafeAreaInsets(); const finish = useSubtextPreferences(s => s.finish);
  return <Page><View style={{ paddingTop: insets.top + 48, gap: 24 }}>
    <Copy title style={{ fontSize: 36 }}>cue.</Copy>
    <Copy style={ui.title}>Wybierz osobę. Poznaj kontekst.</Copy>
    <Copy style={ui.body}>Profile rozmów i podpowiedzi odpowiedzi z Messengera i WhatsAppa.</Copy>
    <Button label="Zaczynamy" onPress={() => { finish(); router.replace('/'); }} />
  </View></Page>;
}
