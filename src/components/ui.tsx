import { Pressable, ScrollView, StyleSheet, Text, View, type ViewProps, type TextProps } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ReactNode } from 'react';
import { useTheme } from '@/theme/useTheme';

export function Screen({ children }: { children: ReactNode }) {
  const { colors } = useTheme(); const insets = useSafeAreaInsets();
  return <ScrollView style={{ backgroundColor: colors.background }} contentInsetAdjustmentBehavior="automatic" contentContainerStyle={{ padding: 24, gap: 24, paddingBottom: insets.bottom + 32 }}>{children}</ScrollView>;
}
export function Copy({ children, title = false, ...rest }: TextProps & { title?: boolean }) {
  const { colors } = useTheme();
  return <Text {...rest} style={[title ? styles.title : styles.body, { color: title ? colors.text : colors.secondaryText }, rest.style]}>{children}</Text>;
}
export function Card({ children, ...rest }: ViewProps) {
  const { colors } = useTheme();
  return <View {...rest} style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }, rest.style]}>{children}</View>;
}
export function Action({ label, onPress, disabled = false, secondary = false }: { label: string; onPress: () => void; disabled?: boolean; secondary?: boolean }) {
  const { colors } = useTheme();
  return <Pressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} style={({ pressed }) => [styles.button, { backgroundColor: secondary ? colors.surface : colors.accent, borderColor: colors.accent, opacity: disabled ? 0.45 : pressed ? 0.7 : 1 }]}><Text style={{ fontWeight: '600', fontSize: 16, color: secondary ? colors.accent : colors.background }}>{label}</Text></Pressable>;
}
export function InlineError({ message }: { message?: string | null }) {
  return message ? <Copy accessibilityRole="alert">{message}</Copy> : null;
}
const styles = StyleSheet.create({
  title: { fontSize: 28, lineHeight: 36, fontWeight: '700' }, body: { fontSize: 16, lineHeight: 24 },
  card: { padding: 16, gap: 12, borderWidth: 1, borderRadius: 16, borderCurve: 'continuous' },
  button: { minHeight: 48, paddingHorizontal: 16, paddingVertical: 12, borderWidth: 1, borderRadius: 12, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
});
