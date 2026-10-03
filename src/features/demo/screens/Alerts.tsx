import { t, useLanguage } from '@/i18n';
import { useState } from 'react';
import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Action, Copy, Icon, IconButton, Row } from '@/components/ui';
import type { Threat } from '@/features/demo/store';
import { isWarning } from '@/features/protection/presentation';
import { useProtection } from '@/features/protection/useProtection';
import { useTheme } from '@/theme/useTheme';

const filters = [{ id: 'all', label: 'Wszystkie' }, { id: 'new', label: 'Nowe' }, { id: 'read', label: 'Przeczytane' }] as const;
type Filter = typeof filters[number]['id'];

function AlertRow({ threat }: { threat: Threat }) {
  const { colors } = useTheme();
  const message = threat.notificationContext?.messages.at(-1);
  const sender = message?.sender || threat.notificationContext?.title;
  return <Pressable accessibilityRole="button"
    accessibilityLabel={`${t(threat.reviewed ? 'Przeczytane' : 'Nowe')}. ${t(threat.title)}. ${t(threat.risk)}. ${threat.source}, ${sender ? `${sender}, ` : ''}${threat.time}`}
    accessibilityHint={t("Otwiera powiadomienie i szczegóły ostrzeżenia.")}
    onPress={() => router.push(`/alert/${threat.id}`)}
    style={({ pressed }) => [styles.card, { backgroundColor: pressed ? colors.secondary : colors.surface, borderColor: colors.border }]}>
    <Row style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
      <Row style={{ flex: 1, gap: 8 }}><Icon name="message" size={20} color={colors.secondaryText} /><Copy style={styles.source}>{threat.source}</Copy></Row>
      {!threat.reviewed && <Row style={{ gap: 6 }}><View style={[styles.dot, { backgroundColor: colors.warning }]} /><Copy style={[styles.meta, { color: colors.warning, fontFamily: 'DMSansMedium' }]}>{t("Nowe")}</Copy></Row>}
    </Row>
    <Row style={{ alignItems: 'flex-start' }}>
      <Copy title style={styles.rowTitle}>{t(threat.title)}</Copy><Icon name="chevron" size={20} color={colors.secondaryText} />
    </Row>
    <Row style={{ gap: 6 }}>
      <Icon name="warning" size={20} color={colors.warning} />
      <Copy style={[styles.source, { color: colors.warning }]}>{t(threat.risk)}</Copy>
    </Row>
  </Pressable>;
}

export default function Alerts() {
  useLanguage();
  const { threats, loadingResults, resultsError, refreshResults } = useProtection();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [filter, setFilter] = useState<Filter>('all');
  const warnings = threats.filter(isWarning);
  const visible = warnings.filter(t => filter === 'all' || (filter === 'read' ? t.reviewed : !t.reviewed));
  const unreadCount = warnings.filter(t => !t.reviewed).length;

  return <View style={{ flex: 1, backgroundColor: colors.background }}>
    <View style={styles.container}>
      <Row style={{ paddingTop: insets.top + 12, paddingHorizontal: 20, paddingBottom: 12, justifyContent: 'space-between' }}>
        <Row style={{ gap: 8 }}><Icon name="shield" size={28} /><Copy title style={{ fontSize: 27, fontFamily: 'ManropeBold' }}>guardian.</Copy></Row>
        <IconButton name="settings" label={t("Otwórz ustawienia")} onPress={() => router.push('/settings')} />
      </Row>
      <FlashList data={visible} keyExtractor={item => item.id} extraData={colors} contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: insets.bottom + 24 }}
        ListHeaderComponent={<View style={styles.heading}>
          <Copy title accessibilityRole="header">{t("Alerty")}</Copy>
          <View style={[styles.filters, { backgroundColor: colors.secondary }]}>
            {filters.map(({ id, label }) => <Pressable key={id} accessibilityRole="button" accessibilityState={{ selected: filter === id }}
              accessibilityLabel={id === 'new' ? `${t(label)}, ${unreadCount}` : t(label)} onPress={() => setFilter(id)}
              style={({ pressed }) => [styles.filter, { backgroundColor: filter === id ? colors.surface : 'transparent', borderColor: filter === id ? colors.border : 'transparent', opacity: pressed ? 0.65 : 1 }]}>
              <Copy style={[styles.filterLabel, { color: filter === id ? colors.text : colors.secondaryText }]}>{t(label)}{id === 'new' && unreadCount > 0 ? ` (${unreadCount})` : ''}</Copy>
            </Pressable>)}
          </View>
        </View>}
        ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
        ListEmptyComponent={<View style={[styles.empty, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <View style={[styles.emptyIcon, { backgroundColor: colors.secondary }]}><Icon name={resultsError ? 'help' : filter === 'read' ? 'history' : 'shield'} size={32} /></View>
          <Copy title style={styles.emptyTitle}>{loadingResults ? t('Wczytuję ostrzeżenia…') : resultsError ? t('Nie można wczytać ostrzeżeń') : filter === 'new' && warnings.length ? t('Wszystko przeczytane') : filter === 'read' && warnings.length ? t('Brak przeczytanych ostrzeżeń') : t('Brak ostrzeżeń')}</Copy>
          {resultsError && <Copy style={styles.emptyCopy}>{t('Spróbuj odświeżyć listę.')}</Copy>}
          {resultsError && <Action label={t("Spróbuj ponownie")} onPress={() => { void refreshResults(); }} />}
        </View>}
        ListFooterComponent={resultsError && visible.length ? <View style={{ paddingTop: 16, gap: 12 }}><Copy accessibilityRole="alert" style={styles.preview}>{t("Nie udało się odświeżyć ostrzeżeń.")}</Copy><Action secondary label={t("Spróbuj ponownie")} onPress={() => { void refreshResults(); }} /></View> : null}
        renderItem={({ item }) => <AlertRow threat={item} />} />
    </View>
  </View>;
}

const styles = StyleSheet.create({
  container: { flex: 1, width: '100%', maxWidth: 520, alignSelf: 'center' },
  heading: { gap: 8, paddingTop: 12, paddingBottom: 20 },
  filters: { flexDirection: 'row', padding: 4, borderRadius: 16, borderCurve: 'continuous', marginTop: 12 },
  filter: { flex: 1, minHeight: 48, paddingHorizontal: 4, paddingVertical: 12, borderRadius: 12, borderCurve: 'continuous', borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  filterLabel: { fontSize: 16, lineHeight: 24, fontFamily: 'DMSansMedium', textAlign: 'center' },
  card: { padding: 20, gap: 12, borderWidth: 1, borderRadius: 16, borderCurve: 'continuous' },
  source: { fontSize: 16, lineHeight: 24, fontFamily: 'DMSansMedium', flexShrink: 1 }, dot: { width: 6, height: 6, borderRadius: 3 },
  rowTitle: { flex: 1, fontSize: 22, lineHeight: 30, letterSpacing: -0.4, fontFamily: 'DMSansSemiBold' },
  preview: { fontSize: 17, lineHeight: 25 }, meta: { fontSize: 14, lineHeight: 22 },
  empty: { paddingHorizontal: 24, paddingVertical: 32, gap: 16, borderWidth: 1, borderRadius: 16, borderCurve: 'continuous', alignItems: 'center' },
  emptyIcon: { width: 64, height: 64, borderRadius: 16, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  emptyTitle: { fontSize: 24, lineHeight: 32, textAlign: 'center' }, emptyCopy: { fontSize: 17, lineHeight: 25, textAlign: 'center' },
});
