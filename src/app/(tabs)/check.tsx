import { useState } from 'react';
import { TextInput, View } from 'react-native';
import { Screen, Copy, Card, Action } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';

export default function CheckMessage() {
  const [message, setMessage] = useState('');
  const [preview, setPreview] = useState(false);
  const { colors } = useTheme();
  return <Screen title="Sprawdź wiadomość">
    <Copy>Wklej wiadomość lub link, który budzi Twoje wątpliwości.</Copy>
    <TextInput accessibilityLabel="Wiadomość lub link do sprawdzenia" placeholder="Tutaj wklej wiadomość…" placeholderTextColor={colors.secondaryText}
      value={message} onChangeText={text => { setMessage(text); setPreview(false); }} multiline textAlignVertical="top"
      style={{ minHeight: 200, padding: 20, borderWidth: 2, borderColor: colors.border, borderRadius: 16, backgroundColor: colors.surface, color: colors.text, fontFamily: 'DMSans', fontSize: 22, lineHeight: 33 }} />
    {!message.trim() && <Copy>Przycisk poniżej włączy się, gdy wpiszesz lub wkleisz wiadomość.</Copy>}
    <Action label="Sprawdź tę wiadomość" disabled={!message.trim()} onPress={() => setPreview(true)} />
    {preview && <Card><Copy title style={{ fontSize: 26, lineHeight: 36 }}>Sprawdzanie nie jest jeszcze dostępne</Copy><Copy>To wersja pokazowa. Ta wiadomość nie została przeanalizowana.</Copy><Action label="Wpisz inną wiadomość" secondary onPress={() => { setMessage(''); setPreview(false); }} /></Card>}
    <View style={{ gap: 12 }}><Copy>Wersja pokazowa — bez analizy wiadomości.</Copy><Copy>Wpisana treść nie jest zapisywana ani wysyłana.</Copy></View>
  </Screen>;
}
