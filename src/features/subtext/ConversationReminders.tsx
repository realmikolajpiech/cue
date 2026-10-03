import { useState } from 'react';
import { Alert, Modal, Pressable, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, Copy, Icon, Row } from '@/components/ui';
import { subtext } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import type { ConversationReminder } from '@/types/subtext';
import { Button, Disclosure, ErrorText, Field, Page, ui } from './components';

const labels: Record<ConversationReminder['effectiveStatus'], string> = {
  open: 'Ważne', tentative: 'Do potwierdzenia', upcoming: 'Nadchodzące', waiting: 'Oczekuje',
  overdue: 'Po terminie', past: 'Termin minął', expired: 'Nieaktualne', done: 'Zakończone', cancelled: 'Odwołane',
};
const archived = new Set(['done', 'cancelled', 'expired']);
function dateLabel(date: string) {
  if (!date) return '';
  if (date.length === 10) return date.split('-').reverse().join('.');
  return new Date(date).toLocaleString('pl-PL', { dateStyle: 'short', timeStyle: 'short' });
}
function editableDate(date: string) {
  if (!date || date.length === 10) return dateLabel(date);
  const value = new Date(date);
  return `${String(value.getDate()).padStart(2, '0')}.${String(value.getMonth() + 1).padStart(2, '0')}.${value.getFullYear()} ${String(value.getHours()).padStart(2, '0')}:${String(value.getMinutes()).padStart(2, '0')}`;
}
function parseDate(value: string) {
  if (!value.trim()) return '';
  const match = /^(\d{2})\.(\d{2})\.(\d{4})(?:\s+(\d{2}):(\d{2}))?$/.exec(value.trim());
  if (!match) throw new Error('Wpisz datę jako DD.MM.RRRR, opcjonalnie z godziną GG:MM.');
  const [, day, month, year, hour = '00', minute = '00'] = match;
  const date = new Date(+year, +month - 1, +day, +hour, +minute);
  if (date.getFullYear() !== +year || date.getMonth() !== +month - 1 || date.getDate() !== +day || date.getHours() !== +hour || date.getMinutes() !== +minute)
    throw new Error('Sprawdź datę i godzinę.');
  return match[4] ? date.toISOString() : `${year}-${month}-${day}`;
}

export default function ConversationReminders({ id, ready, busy, demo, hasMessages }: {
  id: string; ready: boolean; busy: boolean; demo?: boolean; hasMessages: boolean;
}) {
  const { colors } = useTheme(); const client = useQueryClient();
  const queryKey = ['subtext', 'reminders', id];
  const query = useQuery({ queryKey, queryFn: () => subtext.conversationReminders(id), refetchInterval: 60_000 });
  const [editing, setEditing] = useState<ConversationReminder | null>(null);
  const [text, setText] = useState(''); const [date, setDate] = useState(''); const [editError, setEditError] = useState<unknown>();
  const update = useMutation({ mutationFn: ({ item, patch }: { item: ConversationReminder; patch: Parameters<typeof subtext.editConversationReminder>[2] }) => subtext.editConversationReminder(id, item.id, patch),
    onSuccess: async () => { setEditing(null); await client.invalidateQueries({ queryKey: ['subtext'] }); } });
  const refresh = useMutation({ mutationFn: () => subtext.refreshConversationReminders(id), onSuccess: items => {
    client.setQueryData(queryKey, items); void client.invalidateQueries({ queryKey: ['subtext', 'memory', id] });
    void client.invalidateQueries({ queryKey: ['subtext', 'writing-style', id] });
  } });
  const items = query.data ?? [];
  const active = items.filter(item => !archived.has(item.effectiveStatus));
  const history = items.filter(item => archived.has(item.effectiveStatus));
  function openEditor(item: ConversationReminder) { setEditing(item); setText(item.text); setDate(editableDate(item.dueDate)); setEditError(undefined); }
  function renderItem(item: ConversationReminder) {
    return <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={`Edytuj: ${item.text}`} onPress={() => openEditor(item)}
      style={({ pressed }) => ({ paddingVertical: 10, gap: 4, opacity: pressed ? .6 : 1 })}>
      <Row style={{ alignItems: 'flex-start', gap: 10 }}><Icon name={item.effectiveStatus === 'done' ? 'check' : 'history'} size={18} color={colors.accent} />
        <View style={{ flex: 1, gap: 4 }}><Copy selectable style={[ui.body, { color: colors.text }]}>{item.text}</Copy>
          <Copy style={ui.small}>{item.owner === 'me' ? 'Ty' : item.owner === 'other' ? 'Rozmówca' : 'Obie osoby'} · {labels[item.effectiveStatus]}{item.dueDate ? ` · ${dateLabel(item.dueDate)}` : ''}</Copy>
        </View><Icon name="chevron" size={16} color={colors.secondaryText} /></Row>
    </Pressable>;
  }
  return <>
    <Card style={{ padding: 16, gap: 10, borderRadius: 16 }}>
      <Copy title style={ui.title}>Warto pamiętać</Copy>
      {query.isPending ? <Copy style={ui.small}>Odczytuję listę…</Copy> : active.length ? active.map(renderItem) :
        <Copy style={ui.small}>Brak aktualnych spraw. Tutaj pojawią się spotkania, obietnice i ważne informacje z tej rozmowy.</Copy>}
      {!!history.length && <Disclosure label={`Historia (${history.length})`} small>{history.map(renderItem)}</Disclosure>}
      <ErrorText error={query.error ?? refresh.error ?? update.error} />
      {!demo && <Pressable accessibilityRole="button" disabled={!ready || busy || !hasMessages || refresh.isPending}
        onPress={() => refresh.mutate()} style={{ minHeight: 44, justifyContent: 'center', opacity: !ready || busy || !hasMessages || refresh.isPending ? .5 : 1 }}>
        <Copy style={[ui.small, { color: colors.accent, fontFamily: 'DMSansSemiBold' }]}>{refresh.isPending ? 'Uzupełniam listę…' : items.length ? 'Uzupełnij z wiadomości' : 'Znajdź ważne sprawy'}</Copy>
      </Pressable>}
      {!ready && !demo && <Copy style={ui.small}>Włącz analizę AI, aby uzupełniać listę z wiadomości.</Copy>}
    </Card>
    <Modal visible={!!editing} animationType="slide" onRequestClose={() => setEditing(null)}>
      <Page>
        <Button label="Zamknij" secondary onPress={() => setEditing(null)} />
        <Copy title style={ui.heading}>Ważna sprawa</Copy>
        <Field accessibilityLabel="Treść ważnej sprawy" value={text} onChangeText={setText} multiline maxLength={400} />
        <Copy style={ui.small}>Termin (opcjonalnie)</Copy>
        <Field accessibilityLabel="Termin ważnej sprawy" placeholder="05.10.2026 lub 05.10.2026 18:00" value={date} onChangeText={setDate} />
        <ErrorText error={editError ?? update.error} />
        <Button label="Zapisz zmiany" disabled={update.isPending} onPress={() => {
          if (!editing) return;
          try { const dueDate = parseDate(date); if (!text.trim()) throw new Error('Wpisz treść sprawy.'); update.mutate({ item: editing, patch: { text: text.trim(), dueDate } }); }
          catch (error) { setEditError(error); }
        }} />
        {editing && <>
          <Button label={archived.has(editing.effectiveStatus) ? 'Otwórz ponownie' : 'Oznacz jako zakończone'} secondary disabled={update.isPending}
            onPress={() => update.mutate({ item: editing, patch: { status: archived.has(editing.effectiveStatus) ? 'open' : 'done',
              ...(editing.effectiveStatus === 'expired' ? { dueDate: '' } : {}) } })} />
          {!archived.has(editing.effectiveStatus) && <Button label="Oznacz jako odwołane" secondary disabled={update.isPending}
            onPress={() => update.mutate({ item: editing, patch: { status: 'cancelled' } })} />}
          <Button label="Usuń z pamięci" secondary disabled={update.isPending} onPress={() => Alert.alert('Usunąć wpis?', 'Usuniesz tę sprawę z pamięci rozmowy.', [
            { text: 'Anuluj', style: 'cancel' }, { text: 'Usuń', style: 'destructive', onPress: () => update.mutate({ item: editing, patch: { delete: true } }) },
          ])} />
        </>}
      </Page>
    </Modal>
  </>;
}
