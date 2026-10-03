import { type ReactNode } from 'react';
import { ScrollView, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';

export function Page({ children }: { children: ReactNode }) {
  const { colors } = useTheme(); const insets = useSafeAreaInsets();
  return <ScrollView style={{ flex: 1, backgroundColor: colors.background }} contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled"
    contentContainerStyle={{ padding: 24, gap: 24, paddingBottom: insets.bottom + 32, width: '100%', maxWidth: 600, alignSelf: 'center' }}>{children}</ScrollView>;
}
export function Intro({ eyebrow, title, text }: { eyebrow: string; title: string; text?: string }) {
  return <View style={{ gap: 8 }}><Copy style={ui.eyebrow}>{eyebrow}</Copy><Copy title style={ui.heading}>{title}</Copy>{text && <Copy style={ui.body}>{text}</Copy>}</View>;
}
export function Field(props: TextInputProps) {
  const { colors } = useTheme();
  return <TextInput {...props} placeholderTextColor={colors.secondaryText} style={[ui.input, { color: colors.text, backgroundColor: colors.surface, borderColor: colors.border }, props.style]} />;
}
export function ErrorText({ error }: { error: unknown }) { return error ? <Copy accessibilityRole="alert" style={ui.body}>{error instanceof Error ? error.message : 'Nie udało się wykonać działania.'}</Copy> : null; }
export const ui = StyleSheet.create({
  eyebrow: { fontSize: 12, lineHeight: 18, letterSpacing: 1.6, textTransform: 'uppercase', fontFamily: 'DMSansSemiBold' },
  heading: { fontSize: 34, lineHeight: 42, letterSpacing: -1 },
  body: { fontSize: 16, lineHeight: 24 },
  small: { fontSize: 13, lineHeight: 20 },
  title: { fontSize: 21, lineHeight: 29, fontFamily: 'DMSansSemiBold' },
  input: { minHeight: 52, padding: 16, borderWidth: 1, borderRadius: 12, borderCurve: 'continuous', fontSize: 16, lineHeight: 24 },
});
