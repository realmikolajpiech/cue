import { useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-controller';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { useTranslation } from '@/i18n';

export function Page({ children, compact = false }: { children: ReactNode; compact?: boolean }) {
  const { colors } = useTheme(); const insets = useSafeAreaInsets();
  return <KeyboardAwareScrollView bottomOffset={24} style={{ flex: 1, backgroundColor: colors.background }} contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled"
    keyboardDismissMode="on-drag" contentContainerStyle={{ padding: compact ? 16 : 20, gap: compact ? 12 : 20, paddingBottom: insets.bottom + 24, width: '100%', maxWidth: 600, alignSelf: 'center' }}>{children}</KeyboardAwareScrollView>;
}
export function Intro({ eyebrow, title, text }: { eyebrow: string; title: string; text?: string }) {
  return <View style={{ gap: 8 }}><Copy style={ui.eyebrow}>{eyebrow}</Copy><Copy title style={ui.heading}>{title}</Copy>{text && <Copy style={ui.body}>{text}</Copy>}</View>;
}
export function Field(props: TextInputProps) {
  const { colors } = useTheme();
  return <TextInput cursorColor={colors.accent} selectionColor={colors.accent} {...props} placeholderTextColor={colors.secondaryText} style={[ui.input, { color: colors.text, backgroundColor: colors.surface, borderColor: colors.border }, props.style]} />;
}
export function ErrorText({ error }: { error: unknown }) { const { colors } = useTheme(); const { t } = useTranslation(); return error ? <Copy accessibilityRole="alert" style={[ui.body, { color: colors.warning }]}>{error instanceof Error ? error.message : t('common.actionFailed')}</Copy> : null; }
export const ui = StyleSheet.create({
  eyebrow: { fontSize: 12, lineHeight: 18, letterSpacing: 1.6, textTransform: 'uppercase', fontFamily: 'DMSansSemiBold' },
  heading: { fontSize: 34, lineHeight: 42, letterSpacing: -1 },
  body: { fontSize: 16, lineHeight: 24 },
  small: { fontSize: 13, lineHeight: 20 },
  title: { fontSize: 21, lineHeight: 29, fontFamily: 'DMSansSemiBold' },
  input: { minHeight: 52, padding: 16, borderWidth: 1, borderRadius: 16, borderCurve: 'continuous', fontSize: 16, lineHeight: 24, fontFamily: 'DMSans' },
});

export function Button({ label, onPress, disabled = false, secondary = false }: { label: string; onPress: () => void; disabled?: boolean; secondary?: boolean }) {
  const { colors } = useTheme();
  return <Pressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress}
    style={({ pressed }) => ({ minHeight: 52, borderRadius: 28, borderCurve: 'continuous', padding: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: secondary ? colors.secondary : colors.accent, opacity: disabled ? 0.4 : pressed ? 0.75 : 1 })}>
    <Copy style={{ fontSize: 16, lineHeight: 24, fontFamily: 'DMSansSemiBold', color: secondary ? colors.accent : colors.onAccent }}>{label}</Copy>
  </Pressable>;
}
export function Disclosure({ label, children, small = false }: { label: string; children: ReactNode; small?: boolean }) {
  const [open, setOpen] = useState(false); const { colors } = useTheme();
  return <View style={{ gap: open ? 12 : 0 }}>
    <Pressable accessibilityRole="button" accessibilityState={{ expanded: open }} onPress={() => setOpen(value => !value)} style={{ minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Copy style={[small ? ui.small : ui.body, { color: colors.text }]}>{label}</Copy><Copy style={ui.body}>{open ? '−' : '+'}</Copy>
    </Pressable>
    {open && <View style={{ gap: 12 }}>{children}</View>}
  </View>;
}
