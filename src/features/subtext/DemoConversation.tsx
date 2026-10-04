import { useState } from 'react';
import { Alert, View } from 'react-native';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Copy } from '@/components/ui';
import { subtext } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import type { Room } from '@/types/subtext';
import { Button, Disclosure, ErrorText, Field, ui } from './components';

const steps = ['Ustalenie: 50 zł za bilet', 'Zmiana terminu', 'Częściowy zwrot: 20 zł', 'Rozliczenie pozostałych 30 zł'];
export default function DemoConversation({ room, ready, busy }: { room: Room; ready: boolean; busy: boolean }) {
  const client = useQueryClient(); const { colors } = useTheme();
  const [draft, setDraft] = useState('Podziękuj i zaproponuj następne wspólne wyjście, bez ustalania daty.');
  const stage = room.demoStage ?? 0;
  const step = useMutation({ mutationFn: subtext.demoStage, onSuccess: () => client.invalidateQueries({ queryKey: ['subtext'] }) });
  const analyze = useMutation({ mutationFn: () => subtext.analyze(room.id, draft),
    onSuccess: () => client.invalidateQueries({ queryKey: ['subtext'] }) });
  const disabled = busy || step.isPending || analyze.isPending;
  if (!subtext.supportsDemo()) return <Copy style={ui.small}>Zaktualizuj natywny build, aby otworzyć demonstrację pamięci.</Copy>;
  return <View style={{ gap: 12 }}>
    <Copy accessibilityRole="header" style={ui.title}>Zobacz, jak działa pamięć</Copy>
    <Copy style={ui.small}>Fikcyjna rozmowa, prawdziwa analiza AI. Ma osobną pamięć i nie wpływa na Twój ogólny styl. Każdy krok analizujesz samodzielnie.</Copy>
    <Copy style={[ui.body, { color: colors.text }]}>{stage + 1}/4 · {steps[stage]}</Copy>
    <Disclosure label="Wiadomości demonstracyjne">
      {room.messages?.map(message => <View key={message.id} style={{ gap: 4 }}>
        <Copy style={ui.small}>{message.isMe ? 'Ty' : 'Marta'}</Copy>
        <Copy selectable style={[ui.body, { color: colors.text }]}>{message.text}</Copy>
      </View>)}
    </Disclosure>
    <Field value={draft} onChangeText={setDraft} multiline maxLength={1000} accessibilityLabel="Co chcesz wyrazić w demo" placeholder="Co chcesz wyrazić?" />
    <Button label={analyze.isPending ? 'Analizuję rozmowę…' : 'Przeanalizuj ten krok'} disabled={!ready || disabled} onPress={() => analyze.mutate()} />
    {!ready && <Copy style={ui.small}>Włącz analizę AI w ustawieniach i zezwól na nią w tej rozmowie.</Copy>}
    <ErrorText error={analyze.error ?? step.error} />
    {!!room.profile && <Disclosure label="Ostatnie podpowiedzi AI">
      <Copy style={ui.small}>Wygenerowano {new Date(room.profile.createdAt).toLocaleTimeString('pl-PL')}. To propozycje do sprawdzenia, nie wysłane wiadomości.</Copy>
      {room.profile.suggestions.map((suggestion, index) => <View key={index} style={{ gap: 4 }}>
        <Copy style={ui.small}>{suggestion.tone}</Copy>
        <Copy selectable style={[ui.body, { color: colors.text }]}>{suggestion.action === 'no_reply' ? suggestion.reason : suggestion.text}</Copy>
      </View>)}
    </Disclosure>}
    {stage < 3 && <Button label={`Dodaj wiadomości: ${steps[stage + 1].toLowerCase()}`} secondary disabled={disabled}
      onPress={() => { analyze.reset(); step.mutate(stage + 1); }} />}
    <Button label="Zacznij demo od nowa" secondary disabled={disabled} onPress={() => Alert.alert('Zresetować demo?', 'Usuniesz tylko fikcyjną rozmowę i jej pamięć.', [
      { text: 'Anuluj', style: 'cancel' }, { text: 'Zresetuj', onPress: () => { analyze.reset(); step.mutate(0); } },
    ])} />
  </View>;
}
