import { View } from 'react-native';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Action, Card, Copy, Icon } from '@/components/ui';
import { Page, Intro, ui } from './components';
import { useSubtextPreferences } from './preferences';

export default function Onboarding() {
  const insets = useSafeAreaInsets(); const finish = useSubtextPreferences(s => s.finish);
  return <Page><View style={{ paddingTop: insets.top + 32, gap: 36 }}>
    <Copy title style={{ fontSize: 26 }}>subtext.</Copy>
    <Intro eyebrow="Zanim odpowiesz" title={'Dobra rozmowa\nzaczyna się\nod kontekstu.'} text="Pamiętaj ustalenia. Zauważaj wzorce. Odpowiadaj po swojemu." />
    <Card><Icon name="message" size={32} /><Copy style={ui.title}>Twoje rozmowy, w jednym miejscu</Copy><Copy style={ui.body}>Połącz Messengera i WhatsAppa bezpośrednio na telefonie. Przeglądaj dostępne wiadomości i buduj pamięć rozmów.</Copy></Card>
    <View style={{ gap: 12 }}><Copy style={ui.title}>Ty decydujesz, co analizuje AI</Copy><Copy style={ui.body}>Gdy uruchomisz analizę, ostatnie wiadomości wybranej rozmowy i szkic odpowiedzi trafią do DeepSeek. Sesje komunikatorów zostają na telefonie.</Copy><Copy style={ui.small}>Subtext opisuje zachowania w rozmowie, nie diagnozuje osób. Każdą podpowiedź możesz zmienić. Nic nie wysyła automatycznie.</Copy></View>
    <Action label="Przejdź do Subtext" icon="arrow" onPress={() => { finish(); router.replace('/'); }} />
  </View></Page>;
}
