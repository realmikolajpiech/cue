import { useRef, useState } from 'react';
import { Alert, Switch, View } from 'react-native';
import { router } from 'expo-router';
import { Copy, Row } from '@/components/ui';
import { useDemo } from '@/features/demo/store';
import { subtext, subtextCache, useSubtextStatus, useSubtextAction } from '@/services/subtext';
import { Page, Button, Disclosure, Field, ErrorText, ui } from './components';

export default function Settings() {
  const { data: status } = useSubtextStatus(); const action = useSubtextAction(); const key = useRef(''); const [fieldVersion, setFieldVersion] = useState(0);
  const dark = useDemo(s => s.dark); const toggleTheme = useDemo(s => s.toggleTheme);
  return <Page>
    <Button label="Połączone konta" secondary onPress={() => router.push('/connections')} />
    <Disclosure label="Klawiatura">
      <Button label="Włącz klawiaturę Cue" disabled={!status?.available} onPress={() => action.mutate(subtext.keyboardSettings)} />
      <Button label="Wybierz klawiaturę" secondary disabled={!status?.available} onPress={() => action.mutate(async () => subtext.keyboard())} />
    </Disclosure>
    <Row style={{ justifyContent: 'space-between' }}><Copy style={ui.body}>Analiza AI</Copy><Switch accessibilityLabel="Analiza AI" value={status?.cloudEnabled ?? false} disabled={!status?.available || action.isPending}
      onValueChange={enabled => {
        if (!enabled) { action.mutate(() => subtext.cloud(false)); return; }
        Alert.alert('Włączyć analizę AI?', 'DeepSeek otrzyma do 80 ostatnich zapisanych wiadomości wybranej rozmowy, nazwy nadawców, Twój szkic oraz próbki Twoich wiadomości z zapisanych rozmów do dopasowania stylu.', [
          { text: 'Anuluj', style: 'cancel' }, { text: 'Włącz', onPress: () => action.mutate(() => subtext.cloud(true)) },
        ]);
      }} /></Row>
    <Disclosure label={status?.hasApiKey ? 'Klucz AI · zapisany' : 'Dodaj klucz AI'}>
      <Field key={fieldVersion} accessibilityLabel="Klucz API DeepSeek" placeholder="Klucz API DeepSeek" secureTextEntry autoCapitalize="none" autoCorrect={false} onChangeText={value => { key.current = value; }} />
      <Button label="Zapisz" disabled={!status?.available || action.isPending} onPress={() => action.mutate(async () => {
        if (!key.current.trim()) throw new Error('Wpisz klucz API.');
        await subtext.setKey(key.current); key.current = ''; setFieldVersion(v => v + 1);
      })} />
      {status?.hasApiKey && <Button label="Usuń klucz" secondary disabled={action.isPending} onPress={() => action.mutate(() => subtext.setKey(''))} />}
    </Disclosure>
    <Row style={{ justifyContent: 'space-between' }}><Copy style={ui.body}>Ciemny motyw</Copy><Switch accessibilityLabel="Ciemny motyw" value={dark} onValueChange={toggleTheme} /></Row>
    <Disclosure label="Dane i prywatność">
      <Copy style={ui.small}>Analiza wysyła do DeepSeek treść wybranej rozmowy, nazwy nadawców, Twój szkic i próbki Twoich wiadomości z innych zapisanych rozmów do dopasowania stylu. Dane logowania zostają na telefonie. Cue nie wysyła odpowiedzi za Ciebie.</Copy>
      <Button label="Wyczyść dane Cue" secondary disabled={!status?.available || action.isPending} onPress={() => Alert.alert('Wyczyścić dane?', 'Usuniesz lokalne wiadomości i profile. Synchronizacja może pobrać wiadomości ponownie.', [
        { text: 'Anuluj', style: 'cancel' }, { text: 'Wyczyść', style: 'destructive', onPress: () => action.mutate(async () => { await subtext.clear(); subtextCache.removeQueries({ queryKey: ['subtext', 'room'] }); }) },
      ])} />
    </Disclosure>
    <View><ErrorText error={action.error} /></View>
  </Page>;
}
