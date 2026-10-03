import { useState } from 'react';
import { ActivityIndicator, Alert, Modal, Pressable, StyleSheet, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Icon, Row } from '@/components/ui';
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
  function renderItem(item: ConversationReminder, index: number) {
    const detail = [item.owner === 'me' ? 'Ty' : item.owner === 'other' ? 'Rozmówca' : 'Wspólne',
      item.effectiveStatus !== 'open' ? labels[item.effectiveStatus] : '', dateLabel(item.dueDate)].filter(Boolean).join(' · ');
    return <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={`Edytuj: ${item.text}. ${detail}`} onPress={() => openEditor(item)}
      style={({ pressed }) => [styles.item, { borderTopWidth: index ? StyleSheet.hairlineWidth : 0, borderColor: colors.border, opacity: pressed ? .6 : 1 }]}>
      <View style={{ flex: 1, gap: 3 }}>
        <Copy numberOfLines={2} style={[styles.itemText, { color: colors.text }]}>{item.text}</Copy>
        <Copy numberOfLines={2} style={[styles.detail, { color: colors.secondaryText }]}>{detail}</Copy>
      </View>
      <Icon name="chevron" size={15} color={colors.secondaryText} />
    </Pressable>;
  }
  if (!items.length) return null;

  return <>
    <View style={styles.section}>
      <Row style={{ justifyContent: 'space-between', gap: 12 }}>
        <Copy accessibilityRole="header" style={[styles.title, { color: colors.text, flex: 1 }]}>Warto pamiętać</Copy>
        {!demo && <Pressable accessibilityRole="button" accessibilityLabel="Uzupełnij sprawy z wiadomości"
          disabled={!ready || busy || !hasMessages || refresh.isPending} onPress={() => refresh.mutate()}
          style={({ pressed }) => [styles.refresh, { opacity: !ready || busy || !hasMessages ? .35 : pressed ? .6 : 1 }]}>
          {refresh.isPending ? <ActivityIndicator size="small" color={colors.accent} /> : <Icon name="refresh" size={18} color={colors.accent} />}
        </Pressable>}
      </Row>
      <View>{active.map(renderItem)}</View>
      {!!history.length && <Disclosure label={`Historia (${history.length})`} small>{history.map(renderItem)}</Disclosure>}
      <ErrorText error={query.error ?? refresh.error ?? update.error} />
      {!ready && !demo && <Copy style={ui.small}>Włącz analizę AI, aby uzupełniać listę z wiadomości.</Copy>}
    </View>
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

const styles = StyleSheet.create({
  section: { gap: 2, paddingBottom: 12 },
  title: { fontSize: 17, lineHeight: 24, fontFamily: 'DMSansSemiBold' },
  refresh: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  item: { minHeight: 64, paddingVertical: 12, flexDirection: 'row', alignItems: 'center', gap: 14 },
  itemText: { fontSize: 14, lineHeight: 20, fontFamily: 'DMSansMedium' },
  detail: { fontSize: 12, lineHeight: 18 },
});
