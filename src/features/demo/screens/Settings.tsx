import { useState } from 'react';
import { Modal, Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Screen, Copy, Card, Row, Toggle, Action, SectionHeading } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { guardian } from '@/services/guardian';
import { useProtection } from '@/features/protection/useProtection';
import { useDemo } from '@/features/demo/store';

function Setting({ label, description, value, onChange }: { label: string; description?: string; value: boolean; onChange: () => void }) {
  const { colors } = useTheme();
  return <View style={{ gap: 12 }}>
    <Copy style={{ color: colors.text, fontFamily: 'DMSansMedium', fontSize: 22, lineHeight: 32 }}>{label}</Copy>
    {description && <Copy>{description}</Copy>}
    <Row style={{ justifyContent: 'space-between' }}>
      <Copy style={{ color: colors.text }}>{value ? 'Włączone' : 'Wyłączone'}</Copy>
      <Toggle label={label} value={value} onChange={onChange} />
    </Row>
  </View>;
}

export default function Settings() {
  const demo = useDemo();
  const s = useProtection();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [confirm, setConfirm] = useState(false);
  const [advanced, setAdvanced] = useState(false);
  return <Screen title="Ustawienia" header={false} back>
    <Copy>{s.native ? 'Ochrona i ostrzeżenia działają na tym telefonie.' : 'Zmiany dotyczą tylko wersji pokazowej.'}</Copy>
    <Card><Setting label="Ochrona wiadomości" description="Ochronę możesz wyłączyć i ponownie włączyć tutaj." value={s.enabled} onChange={s.toggleProtection} /></Card>
    <Card><Setting label="Pokazuj ostrzeżenia" description="Informuj mnie o podejrzanych wiadomościach." value={s.notifications} onChange={s.toggleNotifications} /></Card>
    <Card><Setting label="Ciemne tło" description="Zmień jasne tło na ciemne." value={demo.dark} onChange={demo.toggleTheme} /></Card>
    <Action label="Jak działa aplikacja?" secondary onPress={() => router.push('/onboarding')} />
    <Action label={advanced ? 'Ukryj dodatkowe ustawienia' : 'Dodatkowe ustawienia'} secondary onPress={() => setAdvanced(v => !v)} />
    {advanced && <View style={{ gap: 24 }}>
      <SectionHeading title="Sprawdzane aplikacje" />
      {Object.entries(s.apps).map(([name, value]) => <Card key={name}><Setting label={name} value={value} onChange={() => { if (!s.native) s.toggleApp(name); }} /></Card>)}
      <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>Twoja prywatność</Copy><Copy>Wiadomości są analizowane na telefonie, bez wysyłania ich do internetu.</Copy></Card>
      <Action secondary label="Usuń ostrzeżenia" onPress={() => setConfirm(true)} />
      {process.env.EXPO_OS === 'android' && <Card><SectionHeading title="Ustawienia techniczne" /><Copy>Konfiguracja modelu na urządzeniu.</Copy><Action secondary label="Dźwięk i banery ostrzeżeń" onPress={() => { void guardian.openWarningChannelSettings(); }} /><Action secondary label="Model i uprawnienia" onPress={() => router.push('/model-setup')} /><Action secondary label="Silnik i test modelu" onPress={() => router.push('/model-settings')} /><Action secondary label="Analizy na urządzeniu" onPress={() => router.push('/analyses')} /></Card>}
    </View>}
    <Modal visible={confirm} transparent animationType="slide" onRequestClose={() => setConfirm(false)}>
      <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: '#00000080' }}>
        <Pressable accessibilityRole="button" accessibilityLabel="Anuluj usuwanie" onPress={() => setConfirm(false)} style={{ flex: 1 }} />
        <View accessibilityViewIsModal style={{ maxHeight: '85%', paddingTop: 28, paddingHorizontal: 24, paddingBottom: insets.bottom + 24, borderTopLeftRadius: 24, borderTopRightRadius: 24, backgroundColor: colors.surface, maxWidth: 520, width: '100%', alignSelf: 'center' }}>
          <ScrollView contentContainerStyle={{ gap: 24 }}>
            <Copy title>Usunąć ostrzeżenia?</Copy>
            <Copy>{s.native ? 'Usuniesz lokalną historię analiz z telefonu.' : 'Usuniesz tylko przykładowe ostrzeżenia z tej wersji pokazowej.'}</Copy>
            <Action secondary label="Nie, zostaw ostrzeżenia" onPress={() => setConfirm(false)} />
            <Action label="Tak, usuń ostrzeżenia" onPress={() => { void s.clear().then(() => setConfirm(false)); }} />
          </ScrollView>
        </View>
      </View>
    </Modal>
  </Screen>;
}
