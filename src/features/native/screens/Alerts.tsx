import { t, useLanguage, dateLocale } from '@/i18n';
import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Card, Copy, Action } from '@/components/ui';
import { useGuardianResults } from '@/services/queries';
import { categoryLabels, riskLabels } from '@/types/guardian';
import { useTheme } from '@/theme/useTheme';

export default function Alerts() { useLanguage();
  const results = useGuardianResults(); const { colors } = useTheme(); const insets = useSafeAreaInsets();
  return <View style={{ flex: 1, backgroundColor: colors.background }}>
    <FlashList data={results.data ?? []} keyExtractor={item => item.id} contentContainerStyle={{ padding: 24, paddingBottom: insets.bottom + 24 }}
      ListHeaderComponent={<View style={{ gap: 12, marginBottom: 24 }}><Copy title>{t("Historia analiz")}</Copy><Copy>{t("Wyniki pozostają na urządzeniu przez 7 dni. Bez treści wiadomości i nazw kontaktów.")}</Copy></View>}
      ListEmptyComponent={<Card><Copy title>{results.isPending ? t("Wczytuję analizy…") : results.isError ? t("Nie można odczytać historii") : t("Nie ma jeszcze analiz")}</Copy><Copy>{results.isError ? t("Spróbuj ponownie.") : t("Po uruchomieniu ochrony znajdziesz tu wyniki nowych powiadomień.")}</Copy>{results.isError && <Action label={t("Spróbuj ponownie")} onPress={() => { void results.refetch(); }} />}</Card>}
      ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
      renderItem={({ item }) => <Pressable accessibilityRole="button" accessibilityLabel={`${t(riskLabels[item.risk])}, ${t(categoryLabels[item.category])}`} onPress={() => router.push(`/analysis/${item.id}`)} style={({ pressed }) => ({ opacity: pressed ? 0.7 : 1 })}><Card><Copy title>{t(riskLabels[item.risk])}</Copy><Copy>{t(categoryLabels[item.category])}</Copy><Copy>{item.sourceApp} · {new Date(item.createdAt).toLocaleString(dateLocale())} · {item.reviewStatus === 'reviewed' ? t("Sprawdzone") : t("Nowe")}</Copy></Card></Pressable>} />
  </View>;
}
