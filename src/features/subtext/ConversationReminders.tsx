import { useState } from 'react';
import { ActivityIndicator, Alert, Modal, Pressable, StyleSheet, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Copy, Icon, Row } from '@/components/ui';
import { subtext } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import type { ConversationReminder } from '@/types/subtext';
import { dateLocale, t as translate, useTranslation } from '@/i18n';
import { Button, Disclosure, ErrorText, Field, Page, ui } from './components';

const archived = new Set(['done', 'cancelled', 'expired']);
function dateLabel(date: string) {
  if (!date) return '';
  if (date.length === 10) return date.split('-').reverse().join('.');
  return new Date(date).toLocaleString(dateLocale(), { dateStyle: 'short', timeStyle: 'short' });
}
function editableDate(date: string) {
  if (!date || date.length === 10) return dateLabel(date);
  const value = new Date(date);
  return `${String(value.getDate()).padStart(2, '0')}.${String(value.getMonth() + 1).padStart(2, '0')}.${value.getFullYear()} ${String(value.getHours()).padStart(2, '0')}:${String(value.getMinutes()).padStart(2, '0')}`;
}
function parseDate(value: string) {
  if (!value.trim()) return '';
  const match = /^(\d{2})\.(\d{2})\.(\d{4})(?:\s+(\d{2}):(\d{2}))?$/.exec(value.trim());
  if (!match) throw new Error(translate('reminders.dateFormatError'));
  const [, day, month, year, hour = '00', minute = '00'] = match;
  const date = new Date(+year, +month - 1, +day, +hour, +minute);
  if (date.getFullYear() !== +year || date.getMonth() !== +month - 1 || date.getDate() !== +day || date.getHours() !== +hour || date.getMinutes() !== +minute)
    throw new Error(translate('reminders.dateInvalid'));
  return match[4] ? date.toISOString() : `${year}-${month}-${day}`;
}

export default function ConversationReminders({ id, ready, busy, demo, hasMessages }: {
  id: string; ready: boolean; busy: boolean; demo?: boolean; hasMessages: boolean;
}) {
  const { colors } = useTheme(); const client = useQueryClient(); const { t } = useTranslation();
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
    const detail = [item.owner === 'me' ? t('common.you') : item.owner === 'other' ? t('reminders.other') : t('reminders.shared'),
      item.effectiveStatus !== 'open' ? t(`reminders.status.${item.effectiveStatus}`) : '', dateLabel(item.dueDate)].filter(Boolean).join(' · ');
    return <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={t('reminders.editLabel', { text: item.text, detail })} onPress={() => openEditor(item)}
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
        <Copy accessibilityRole="header" style={[styles.title, { color: colors.text, flex: 1 }]}>{t('reminders.title')}</Copy>
        {!demo && <Pressable accessibilityRole="button" accessibilityLabel={t('reminders.fillLabel')}
          accessibilityState={{ disabled: !ready || busy || !hasMessages || refresh.isPending, busy: refresh.isPending }}
          disabled={!ready || busy || !hasMessages || refresh.isPending} onPress={() => refresh.mutate()}
          style={({ pressed }) => [styles.refresh, { opacity: !ready || busy || !hasMessages ? .35 : pressed ? .6 : 1 }]}>
          {refresh.isPending ? <ActivityIndicator size="small" color={colors.accent} /> : <Copy style={[styles.refreshLabel, { color: colors.accent }]}>{t('reminders.fill')}</Copy>}
        </Pressable>}
      </Row>
      <View>{active.map(renderItem)}</View>
      {!!history.length && <Disclosure label={t('reminders.history', { count: history.length })} small>{history.map(renderItem)}</Disclosure>}
      <ErrorText error={query.error ?? refresh.error ?? update.error} />
      {!ready && !demo && <Copy style={ui.small}>{t('reminders.enableAI')}</Copy>}
    </View>
    <Modal visible={!!editing} animationType="slide" onRequestClose={() => setEditing(null)}>
      <Page>
        <Button label={t('common.close')} secondary onPress={() => setEditing(null)} />
        <Copy title style={ui.heading}>{t('reminders.editorTitle')}</Copy>
        <Field accessibilityLabel={t('reminders.textLabel')} value={text} onChangeText={setText} multiline maxLength={400} />
        <Copy style={ui.small}>{t('reminders.dueDate')}</Copy>
        <Field accessibilityLabel={t('reminders.dueDateLabel')} placeholder={t('reminders.dueDatePlaceholder')} value={date} onChangeText={setDate} />
        <ErrorText error={editError ?? update.error} />
        <Button label={t('reminders.save')} disabled={update.isPending} onPress={() => {
          if (!editing) return;
          try { const dueDate = parseDate(date); if (!text.trim()) throw new Error(t('reminders.textRequired')); update.mutate({ item: editing, patch: { text: text.trim(), dueDate } }); }
          catch (error) { setEditError(error); }
        }} />
        {editing && <>
          <Button label={archived.has(editing.effectiveStatus) ? t('reminders.reopen') : t('reminders.markDone')} secondary disabled={update.isPending}
            onPress={() => update.mutate({ item: editing, patch: { status: archived.has(editing.effectiveStatus) ? 'open' : 'done',
              ...(editing.effectiveStatus === 'expired' ? { dueDate: '' } : {}) } })} />
          {!archived.has(editing.effectiveStatus) && <Button label={t('reminders.markCancelled')} secondary disabled={update.isPending}
            onPress={() => update.mutate({ item: editing, patch: { status: 'cancelled' } })} />}
          <Button label={t('reminders.remove')} secondary disabled={update.isPending} onPress={() => Alert.alert(t('reminders.removeTitle'), t('reminders.removeMessage'), [
            { text: t('common.cancel'), style: 'cancel' }, { text: t('common.delete'), style: 'destructive', onPress: () => update.mutate({ item: editing, patch: { delete: true } }) },
          ])} />
        </>}
      </Page>
    </Modal>
  </>;
}

const styles = StyleSheet.create({
  section: { gap: 2, paddingBottom: 12 },
  title: { fontSize: 17, lineHeight: 24, fontFamily: 'DMSansSemiBold' },
  refresh: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 4 },
  refreshLabel: { fontSize: 13, lineHeight: 20, fontFamily: 'DMSansMedium' },
  item: { minHeight: 64, paddingVertical: 12, flexDirection: 'row', alignItems: 'center', gap: 14 },
  itemText: { fontSize: 14, lineHeight: 20, fontFamily: 'DMSansMedium' },
  detail: { fontSize: 12, lineHeight: 18 },
});
