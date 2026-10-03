import { Alert, Switch, View } from 'react-native';
import { router } from 'expo-router';
import { Copy, Row } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { usePreferences } from '@/features/preferences';
import { useDemo } from '@/features/demo/store';
import { subtext, subtextCache, useSubtextStatus, useSubtextAction } from '@/services/subtext';
import { Page, Button, Disclosure, ErrorText, ui } from './components';
import { useSubtextPreferences } from './preferences';
import { CueCompanion, CueMark } from '@/components/CueBrand';

export default function Settings() {
  const { data: status } = useSubtextStatus(); const action = useSubtextAction();
  const dark = useDemo(s => s.dark); const toggleTheme = useDemo(s => s.toggleTheme);
  const developerMode = usePreferences(state => state.developerMode);
  const toggleDeveloperMode = usePreferences(state => state.toggleDeveloperMode);
  const { colors } = useTheme();
  const selectPerson = useSubtextPreferences(s => s.selectPerson);
  return <Page>
    <CueCompanion title="Cue po Twojemu" text="Twoje konta, Twój styl i Twoje decyzje. Ustaw pomoc tak, jak lubisz." />
    <Button label="Połączone konta" secondary onPress={() => router.push('/connections')} />
    <Disclosure label="Klawiatura">
      <Button label="Włącz klawiaturę Cue" disabled={!status?.available} onPress={() => action.mutate(subtext.keyboardSettings)} />
      <Button label="Wybierz klawiaturę" secondary disabled={!status?.available} onPress={() => action.mutate(async () => subtext.keyboard())} />
    </Disclosure>
    <Disclosure label="Poznaj Cue">
      <CueMark />
      <Copy style={ui.small}>Sprawdź podsumowanie i odpowiedzi na przykładowej rozmowie, zanim skorzystasz z własnych wiadomości.</Copy>
      <Button label="Otwórz rozmowę przykładową" secondary disabled={!status?.available || action.isPending} onPress={() => action.mutate(async () => {
        const id = await subtext.demo(); selectPerson(id);
        router.push({ pathname: '/person/[id]', params: { id } });
      })} />
    </Disclosure>
    <Row style={{ justifyContent: 'space-between' }}><Copy style={ui.body}>Analiza AI</Copy><Switch accessibilityLabel="Analiza AI" thumbColor={colors.surface} trackColor={{ false: colors.border, true: colors.accent }} value={status?.cloudEnabled ?? false} disabled={!status?.available || action.isPending}
      onValueChange={enabled => {
        if (!enabled) { action.mutate(() => subtext.cloud(false)); return; }
        Alert.alert('Włączyć analizę AI?', 'Supabase przekaże do DeepSeek do 80 ostatnich wiadomości, nazwy nadawców, szkic i pamięć wybranego czatu. Pamięć kontekstu prywatnych rozmów będzie też automatycznie uzupełniana w partiach po synchronizacji. Cechy stylu i częste zwroty liczymy na telefonie.', [
          { text: 'Anuluj', style: 'cancel' }, { text: 'Włącz', onPress: () => action.mutate(() => subtext.cloud(true)) },
        ]);
      }} /></Row>
    <Copy style={ui.small}>Klucz AI jest przechowywany bezpiecznie na serwerze. Nie musisz wpisywać go na telefonie.</Copy>
    <Row style={{ justifyContent: 'space-between' }}><Copy style={ui.body}>Ciemny motyw</Copy><Switch accessibilityLabel="Ciemny motyw" thumbColor={colors.surface} trackColor={{ false: colors.border, true: colors.accent }} value={dark} onValueChange={toggleTheme} /></Row>
    <Row style={{ justifyContent: 'space-between' }}><Copy style={ui.body}>Tryb deweloperski</Copy><Switch accessibilityLabel="Tryb deweloperski" thumbColor={colors.surface} trackColor={{ false: colors.border, true: colors.accent }} value={developerMode} onValueChange={toggleDeveloperMode} /></Row>
    <Disclosure label="Dane i prywatność">
      <Copy style={ui.small}>Analiza przesyła przez Supabase do DeepSeek ostatnie 80 wiadomości, pamięć czatu i Twój szkic. Po włączeniu AI kontekst prywatnych rozmów uzupełnia się też automatycznie w partiach. Styl aktualizuje się lokalnie po synchronizacji. Cue nie wysyła odpowiedzi za Ciebie.</Copy>
      <Button label="Wyczyść dane Cue" secondary disabled={!status?.available || action.isPending} onPress={() => Alert.alert('Wyczyścić dane?', 'Usuniesz lokalne wiadomości i profile. Synchronizacja może pobrać wiadomości ponownie.', [
        { text: 'Anuluj', style: 'cancel' }, { text: 'Wyczyść', style: 'destructive', onPress: () => action.mutate(async () => { await subtext.clear(); subtextCache.removeQueries({ queryKey: ['subtext', 'room'] }); }) },
      ])} />
    </Disclosure>
    <View><ErrorText error={action.error} /></View>
  </Page>;
}
