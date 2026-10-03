import { t, useLanguage } from '@/i18n';
import { useState } from 'react';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { Screen, Copy, Empty, Icon } from '@/components/ui';
import type { Threat } from '@/features/demo/store';
import { useProtection } from '@/features/protection/useProtection';
import { useTheme } from '@/theme/useTheme';

const filters = [
  { id: 'all', label: 'Wszystkie' },
  { id: 'new', label: 'Nowe' },
  { id: 'read', label: 'Przeczytane' },
] as const;
type Filter = typeof filters[number]['id'];

function AlertRow({ threat, last }: { threat: Threat; last: boolean }) { useLanguage();
  const { colors } = useTheme();
  const status = threat.reviewed ? t("Przeczytane") : t("Nowe");
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${status}. ${threat.title}. ${threat.source}, ${threat.time}`}
      accessibilityHint={t("Otwiera ostrzeżenie i wskazówki, co zrobić.")}
      onPress={() => router.push(`/alert/${threat.id}`)}
      style={({ pressed }) => [styles.row, {
        backgroundColor: pressed ? colors.secondary : colors.surface,
        borderBottomColor: colors.border,
        borderBottomWidth: last ? 0 : 1,
      }]}
    >
      <View style={[styles.icon, { backgroundColor: threat.reviewed ? colors.secondary : colors.warningSoft }]}>
        <Icon name={threat.reviewed ? 'check' : 'warning'} size={26} color={threat.reviewed ? colors.secondaryText : colors.warning} />
      </View>
      <View style={styles.content}>
        <Copy title style={styles.rowTitle}>{threat.title}</Copy>
        <Copy style={styles.metadata}>{threat.source} · {threat.time}</Copy>
        <Copy style={[styles.status, { color: threat.reviewed ? colors.secondaryText : colors.warning }]}>{status}</Copy>
      </View>
      <Icon name="chevron" size={20} color={colors.secondaryText} />
    </Pressable>
  );
}

export default function Alerts() { useLanguage();
  const { threats } = useProtection();
  const { colors } = useTheme();
  const [filter, setFilter] = useState<Filter>('all');
  const visible = threats.filter(t => filter === 'all' || (filter === 'read' ? t.reviewed : !t.reviewed));
  const unreadCount = threats.filter(t => !t.reviewed).length;

  return (
    <Screen title={t("Ostrzeżenia")}>
      <View style={styles.body}>
        <View style={[styles.filters, { backgroundColor: colors.secondary }]}>
          {filters.map(({ id, label }) => (
            <Pressable
              key={id}
              accessibilityRole="button"
              accessibilityLabel={id === 'new' ? `${t(label)}, ${unreadCount}` : t(label)}
              accessibilityState={{ selected: filter === id }}
              onPress={() => setFilter(id)}
              style={({ pressed }) => [styles.filter, {
                backgroundColor: filter === id ? colors.surface : 'transparent',
                borderColor: filter === id ? colors.border : 'transparent',
                opacity: pressed ? 0.65 : 1,
              }]}
            >
              <Copy style={[styles.filterLabel, { color: filter === id ? colors.text : colors.secondaryText }]}>{t(label)}</Copy>
              {id === 'new' && unreadCount > 0 && <View style={[styles.count, { backgroundColor: colors.warningSoft }]}>
                <Copy style={[styles.countLabel, { color: colors.warning }]}>{unreadCount}</Copy>
              </View>}
            </Pressable>
          ))}
        </View>
        {visible.length > 0 ? (
          <View style={[styles.list, { backgroundColor: colors.surface, borderColor: colors.border }]}>
            {visible.map((threat, index) => <AlertRow key={threat.id} threat={threat} last={index === visible.length - 1} />)}
          </View>
        ) : (
          <Empty
            title={filter === 'new' && threats.length ? t("Wszystko przeczytane") : filter === 'read' && threats.length ? t("Brak przeczytanych ostrzeżeń") : t("Nie ma ostrzeżeń")}
            subtitle={threats.length ? t("Pozostałe ostrzeżenia znajdziesz w zakładce „Wszystkie”.") : t("Nowe ostrzeżenia pojawią się tutaj.")}
          />
        )}
        {visible.length > 0 && <Copy style={styles.hint}>{t("Dotknij ostrzeżenia, aby zobaczyć, co zrobić.")}</Copy>}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: 20 },
  filters: { flexDirection: 'row', padding: 4, borderRadius: 28, gap: 2 },
  filter: { flex: 1, minHeight: 52, paddingHorizontal: 4, paddingVertical: 10, borderRadius: 24, borderWidth: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4, flexWrap: 'wrap' },
  filterLabel: { fontSize: 16, lineHeight: 23, fontFamily: 'DMSansMedium', textAlign: 'center', flexShrink: 1 },
  count: { minWidth: 24, minHeight: 24, paddingHorizontal: 5, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  countLabel: { fontSize: 14, lineHeight: 20, fontFamily: 'DMSansMedium' },
  list: { borderWidth: 1, borderRadius: 24, overflow: 'hidden' },
  row: { minHeight: 124, paddingHorizontal: 16, paddingVertical: 20, flexDirection: 'row', alignItems: 'center', gap: 12 },
  icon: { width: 48, height: 48, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  content: { flex: 1, gap: 6 },
  rowTitle: { fontSize: 20, lineHeight: 27, letterSpacing: -0.3, fontFamily: 'DMSansMedium' },
  metadata: { fontSize: 16, lineHeight: 23 },
  status: { fontSize: 15, lineHeight: 21, fontFamily: 'DMSansMedium' },
  hint: { fontSize: 17, lineHeight: 25, paddingHorizontal: 4 },
});
