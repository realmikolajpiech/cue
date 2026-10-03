import { useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Modal, Platform, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy, Icon, Action, Row } from '@/components/ui';
import { guardian } from '@/services/guardian';
import { useTheme } from '@/theme/useTheme';
import { t, dateLocale, useLanguage } from '@/i18n';
import type { GuardianNotification } from '@/types/guardian';

export function NotificationPicker({ visible, onClose, onSelect }: { visible: boolean; onClose: () => void; onSelect: (item: GuardianNotification) => void }) {
  useLanguage();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [items, setItems] = useState<GuardianNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!visible) return;
    let live = true;
    void guardian.notifications().then(data => { if (live) setItems(data); }).catch(cause => {
      if (live) setError(cause instanceof Error && cause.message.includes('notification_history_build_required')
        ? 'Historia powiadomień wymaga nowego buildu Android.' : 'Nie udało się odczytać powiadomień.');
    }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [visible, attempt]);
  return <Modal visible={visible} animationType="slide" presentationStyle={Platform.OS === 'ios' ? 'pageSheet' : 'overFullScreen'} transparent={Platform.OS !== 'ios'} allowSwipeDismissal onRequestClose={onClose}>
    <View style={[styles.overlay, { backgroundColor: Platform.OS === 'ios' ? colors.surface : '#00000066' }]}>
      {Platform.OS !== 'ios' && <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityRole="button" accessibilityLabel={t('Zamknij')} />}
      <View accessibilityViewIsModal style={[styles.sheet, { backgroundColor: colors.surface, paddingBottom: Math.max(insets.bottom, 20), maxHeight: Platform.OS === 'ios' ? '100%' : '85%', paddingTop: Platform.OS === 'ios' ? 24 : 12, ...(Platform.OS === 'ios' ? { flex: 1 } : {}) }]}>
        {Platform.OS !== 'ios' && <View style={[styles.handle, { backgroundColor: colors.border }]} />}
        <Row style={{ justifyContent: 'space-between', paddingHorizontal: 20 }}>
          <Copy title style={{ fontSize: 26, lineHeight: 34, flex: 1 }}>{t('Wybierz powiadomienie')}</Copy>
          <Pressable accessibilityRole="button" accessibilityLabel={t('Zamknij')} onPress={onClose} style={[styles.close, { backgroundColor: colors.secondary }]}><Icon name="close" size={22} /></Pressable>
        </Row>
        <Copy style={styles.note}>{t('Zebrane podczas działania ochrony · ostatnie 15 minut')}</Copy>
        {loading ? <ActivityIndicator style={{ padding: 36 }} color={colors.text} accessibilityLabel={t('Wczytuję powiadomienia…')} /> : error ? <View style={styles.empty}><Copy accessibilityRole="alert">{t(error)}</Copy><Action secondary label={t('Spróbuj ponownie')} onPress={() => { setLoading(true); setError(null); setAttempt(a => a + 1); }} /></View> : <FlatList
          data={items} keyExtractor={item => item.id} contentContainerStyle={{ paddingHorizontal: 20 }}
          ListEmptyComponent={<View style={styles.empty}><Icon name="bell" size={30} /><Copy title style={{ fontSize: 23, lineHeight: 31 }}>{t('Brak powiadomień')}</Copy><Copy>{t(Platform.OS === 'ios' ? 'iOS nie udostępnia powiadomień innych aplikacji. Wklej treść wiadomości lub link.' : 'Włącz ochronę i dostęp do powiadomień. Nowe wiadomości z obsługiwanych aplikacji pojawią się tutaj.')}</Copy></View>}
          renderItem={({ item }) => <Pressable accessibilityRole="button" accessibilityLabel={`${item.sourceApp}, ${item.text}`} onPress={() => { onSelect(item); onClose(); }} style={({ pressed }) => [styles.item, { borderColor: colors.border, opacity: pressed ? 0.6 : 1 }]}>
            <View style={[styles.icon, { backgroundColor: colors.secondary }]}><Icon name="message" size={24} /></View>
            <View style={{ flex: 1, gap: 6 }}><Row style={{ flexWrap: 'wrap', justifyContent: 'space-between' }}><Copy style={{ color: colors.text, fontFamily: 'DMSansMedium', fontSize: 18 }}>{item.sourceApp}</Copy><Copy style={{ fontSize: 15, lineHeight: 22 }}>{new Date(item.createdAt).toLocaleTimeString(dateLocale(), { hour: '2-digit', minute: '2-digit' })}</Copy></Row><Copy numberOfLines={3} style={{ color: colors.text, fontSize: 18, lineHeight: 26 }}>{item.text}</Copy></View><Icon name="chevron" size={18} />
          </Pressable>} />}
      </View>
    </View>
  </Modal>;
}
const styles = StyleSheet.create({ overlay: { flex: 1, justifyContent: 'flex-end' }, sheet: { borderTopLeftRadius: 28, borderTopRightRadius: 28, flexShrink: 1, minHeight: 360 }, handle: { width: 40, height: 5, borderRadius: 4, alignSelf: 'center', marginBottom: 18 }, close: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' }, note: { paddingHorizontal: 20, paddingVertical: 16, fontSize: 16, lineHeight: 24 }, empty: { paddingVertical: 28, paddingHorizontal: 20, gap: 16 }, item: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 20, borderBottomWidth: 1 }, icon: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' } });
