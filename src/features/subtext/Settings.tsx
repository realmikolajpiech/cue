import { useRef, useState } from 'react';
import { Alert, Switch, View } from 'react-native';
import { Action, Card, Copy, Row } from '@/components/ui';
import { useDemo } from '@/features/demo/store';
import { subtext, subtextCache, useSubtextStatus, useSubtextAction } from '@/services/subtext';
import { Page, Intro, Field, ErrorText, ui } from './components';

export default function Settings() {
  const { data: status } = useSubtextStatus(); const action = useSubtextAction(); const key = useRef(''); const [fieldVersion, setFieldVersion] = useState(0);
  const dark = useDemo(s => s.dark); const toggleTheme = useDemo(s => s.toggleTheme);
  return <Page><Intro eyebrow="Subtext" title="Twoje zasady." text="Połączenia na telefonie. Analiza na Twoje żądanie." />
    <Card><Copy style={ui.title}>DeepSeek V4.1 Flash</Copy><Copy style={ui.body}>{status?.hasApiKey ? 'Klucz API zapisany na tym telefonie.' : 'Dodaj klucz API, aby korzystać z podpowiedzi.'}</Copy>
      <Field key={fieldVersion} accessibilityLabel="Klucz API DeepSeek" placeholder="Klucz API DeepSeek" secureTextEntry autoCapitalize="none" autoCorrect={false} onChangeText={value => { key.current = value; }} />
      <Action label="Zapisz klucz" disabled={!status?.available || action.isPending} onPress={() => action.mutate(async () => {
        if (!key.current.trim()) throw new Error('Wpisz klucz API.');
        await subtext.setKey(key.current); key.current = ''; setFieldVersion(v => v + 1);
      })} />
      <Row style={{ justifyContent: 'space-between' }}><Copy style={[ui.body, { flex: 1 }]}>Analiza w DeepSeek</Copy><Switch accessibilityLabel="Zezwól na analizę w DeepSeek" value={status?.cloudEnabled ?? false} disabled={!status?.available || action.isPending}
        onValueChange={enabled => action.mutate(() => subtext.cloud(enabled))} /></Row>
      <Copy style={ui.small}>Analiza wysyła do DeepSeek do 80 ostatnich zapisanych wiadomości z wybranej rozmowy oraz Twój szkic. Obejmuje ich treść i wyświetlane nazwy nadawców. Nie wysyła danych logowania do komunikatorów.</Copy>
      {status?.hasApiKey && <Action label="Usuń klucz API" secondary disabled={action.isPending} onPress={() => action.mutate(() => subtext.setKey(''))} />}
    </Card>
    <Card><Copy style={ui.title}>Podpowiedzi podczas pisania</Copy><Copy style={ui.body}>Włącz klawiaturę Subtext. W komunikatorze wybierz rozmówcę i dotknij „Podpowiedz odpowiedź”. Dotknięcie propozycji wstawia ją do pola — Ty ją wysyłasz.</Copy>
      <Action label="Włącz klawiaturę Subtext" disabled={!status?.available} onPress={() => action.mutate(subtext.keyboardSettings)} />
      <Action label="Wybierz klawiaturę" secondary disabled={!status?.available} onPress={() => action.mutate(async () => subtext.keyboard())} />
      <Copy style={ui.small}>Przytrzymaj literę, aby wpisać polski znak. Przycisk „…” przełącza klawiatury. Podpowiedzi są wyłączone w polach haseł.</Copy>
    </Card>
    <Card><Row style={{ justifyContent: 'space-between' }}><Copy style={ui.title}>Ciemny motyw</Copy><Switch accessibilityLabel="Ciemny motyw" value={dark} onValueChange={toggleTheme} /></Row></Card>
    <View style={{ gap: 12 }}><Copy style={ui.title}>Pamięć na urządzeniu</Copy><Copy style={ui.body}>Subtext zachowuje do 200 wiadomości w każdej z maksymalnie 150 rozmów oraz ostatni profil AI. Dane są w prywatnym katalogu aplikacji. Wyczyszczenie pamięci nie usuwa rozmów z komunikatora; synchronizacja może pobrać je ponownie.</Copy>
      <Action label="Wyczyść pamięć Subtext" secondary disabled={!status?.available || action.isPending} onPress={() => Alert.alert('Wyczyścić pamięć?', 'Usuniesz lokalne wiadomości i profile. Aby zatrzymać ponowne pobieranie, odłącz też komunikator.', [
        { text: 'Anuluj', style: 'cancel' }, { text: 'Wyczyść', style: 'destructive', onPress: () => action.mutate(async () => { await subtext.clear(); subtextCache.removeQueries({ queryKey: ['subtext', 'room'] }); }) },
      ])} />
    </View><ErrorText error={action.error} />
  </Page>;
}
