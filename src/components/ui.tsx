import { t, useLanguage } from '@/i18n';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, View, type ViewProps, type TextProps, type ColorValue } from 'react-native';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ReactNode } from 'react';
import { useTheme } from '@/theme/useTheme';
const icons = {
  style: { ios: 'text.bubble', android: 'edit_note', web: 'edit_note' },
  back: { ios: 'arrow.left', android: 'arrow_back', web: 'arrow_back' }, shield: { ios: 'shield', android: 'shield', web: 'shield' }, scan: { ios: 'viewfinder', android: 'document_scanner', web: 'document_scanner' }, history: { ios: 'clock.arrow.circlepath', android: 'history', web: 'history' }, settings: { ios: 'gearshape', android: 'settings', web: 'settings' }, arrow: { ios: 'arrow.right', android: 'arrow_forward', web: 'arrow_forward' }, external: { ios: 'arrow.up.right', android: 'north_east', web: 'north_east' }, chevron: { ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }, message: { ios: 'message', android: 'chat_bubble', web: 'chat_bubble' }, lock: { ios: 'lock', android: 'lock', web: 'lock' }, moon: { ios: 'moon', android: 'dark_mode', web: 'dark_mode' }, sun: { ios: 'sun.max', android: 'light_mode', web: 'light_mode' }, bell: { ios: 'bell', android: 'notifications', web: 'notifications' }, warning: { ios: 'exclamationmark.triangle', android: 'warning', web: 'warning' }, check: { ios: 'checkmark', android: 'check', web: 'check' }, phone: { ios: 'phone', android: 'call', web: 'call' }, close: { ios: 'xmark', android: 'close', web: 'close' }, help: { ios: 'questionmark.circle', android: 'help', web: 'help' },
} satisfies Record<string, SymbolViewProps['name']>;
export type IconName = keyof typeof icons;
export function Icon({ name, size = 21, color }: { name: IconName; size?: number; color?: ColorValue }) { const { colors } = useTheme(); return <SymbolView name={icons[name]} size={size} tintColor={color ?? colors.text} />; }
export function Row({ children, ...rest }: ViewProps) { return <View {...rest} style={[styles.row, rest.style]}>{children}</View>; }
export function Copy({ children, title = false, ...rest }: TextProps & { title?: boolean }) { const { colors } = useTheme(); return <Text {...rest} style={[title ? styles.title : styles.body, { color: title ? colors.text : colors.secondaryText, fontFamily: title ? 'Manrope' : 'DMSans' }, rest.style]}>{children}</Text>; }
export function IconButton({ name, label, onPress }: { name: IconName; label: string; onPress: () => void }) {
  const { colors } = useTheme();
  return <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress}
    style={({ pressed }) => ({ width: 60, height: 60, alignItems: 'center', justifyContent: 'center', borderRadius: 16, backgroundColor: colors.secondary, opacity: pressed ? .6 : 1 })}>
    <Icon name={name} size={28} />
  </Pressable>;
}
export function Screen({ children, title, header = true, back = false, centered = false, footer }: { children: ReactNode; title?: string; header?: boolean; back?: boolean; centered?: boolean; footer?: ReactNode }) {
  useLanguage();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  return <View style={{ flex: 1, backgroundColor: colors.background }}>
    <View style={{ width: '100%', maxWidth: 520, alignSelf: 'center', flex: 1 }}>
      {(header || back) && <Row style={{ paddingTop: insets.top + 12, paddingHorizontal: 20, paddingBottom: 12, justifyContent: 'space-between' }}>
        {back ? <Pressable accessibilityRole="button" onPress={() => router.canGoBack() ? router.back() : router.replace('/')} style={{ minHeight: 60, justifyContent: 'center', paddingRight: 20 }}>
          <Row style={{ gap: 10 }}><Icon name="back" size={26} /><Copy style={{ color: colors.text, fontFamily: 'DMSansMedium' }}>{t("Wróć")}</Copy></Row>
        </Pressable> : <Row style={{ gap: 8 }}><Icon name="shield" size={28} /><Text style={{ color: colors.text, fontSize: 27, fontFamily: 'ManropeBold', letterSpacing: -1 }}>guardian.</Text></Row>}
        {!back && <IconButton name="settings" label={t("Otwórz ustawienia")} onPress={() => router.push('/settings')} />}
      </Row>}
      <KeyboardAvoidingView style={{ flex: 1 }} enabled={!!footer} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ScrollView style={{ flex: 1 }} keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 20, paddingTop: centered ? 20 : header || back ? 12 : 24, gap: 24, paddingBottom: footer ? 20 : centered ? 20 : insets.bottom + 32, ...(centered ? { flexGrow: 1, justifyContent: 'center' as const } : {}) }}>
        {title && <Copy title accessibilityRole="header">{title}</Copy>}{children}
      </ScrollView>
      {footer && <View style={{ paddingHorizontal: 20, paddingTop: 12, paddingBottom: Math.max(insets.bottom, 16), gap: 12, backgroundColor: colors.background }}>{footer}</View>}
      </KeyboardAvoidingView>
    </View>
  </View>;
}
export function Card({ children, ...rest }: ViewProps) { const { colors } = useTheme(); return <View {...rest} style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }, rest.style]}>{children}</View>; }
export function Action({ label, onPress, disabled = false, secondary = false, icon }: { label: string; onPress: () => void; disabled?: boolean; secondary?: boolean; icon?: IconName }) { const { colors } = useTheme(); return <Pressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} style={({ pressed }) => [styles.button, { backgroundColor: secondary ? colors.surface : colors.text, borderColor: secondary ? colors.border : colors.text, opacity: disabled ? .4 : pressed ? .7 : 1 }]}><Text style={{ fontFamily: 'DMSansMedium', fontSize: 20, lineHeight: 28, textAlign: 'center', flexShrink: 1, color: secondary ? colors.text : colors.surface }}>{label}</Text>{icon && <Icon name={icon} size={25} color={secondary ? colors.text : colors.surface} />}</Pressable>; }
export function TextAction({ label, onPress }: { label: string; onPress: () => void }) { const { colors } = useTheme(); return <Pressable onPress={onPress} accessibilityRole="button" style={{ minHeight: 60, justifyContent: 'center' }}><Row style={{ gap: 5 }}><Copy style={{ fontSize: 20, lineHeight: 28, color: colors.text }}>{label}</Copy><Icon name="chevron" size={24} /></Row></Pressable>; }
export function SectionHeading({ title, children }: { title: string; children?: ReactNode }) { const { colors } = useTheme(); return <Row style={{ justifyContent: 'space-between', marginBottom: 8 }}><Text style={{ color: colors.text, fontSize: 22, lineHeight: 30, fontFamily: 'DMSansMedium', flexShrink: 1 }}>{title}</Text>{children}</Row>; }
export function Toggle({ value, onChange, label }: { value: boolean; onChange: () => void; label: string }) { const { colors } = useTheme(); return <Pressable accessibilityRole="switch" accessibilityLabel={label} accessibilityState={{ checked: value }} aria-checked={value} onPress={onChange} style={{ minWidth: 72, minHeight: 60, alignItems: 'center', justifyContent: 'center' }}><View style={{ width: 60, height: 36, padding: 5, borderRadius: 30, backgroundColor: value ? colors.text : '#B5B5B5' }}><View style={{ width: 26, height: 26, borderRadius: 20, backgroundColor: colors.surface, alignSelf: value ? 'flex-end' : 'flex-start' }} /></View></Pressable>; }
export function Tags({ values }: { values: string[] }) { const { colors } = useTheme(); return <Row style={{ flexWrap: 'wrap', gap: 6 }}>{values.map(v => <Copy key={v} style={{ fontSize: 18, lineHeight: 27, backgroundColor: colors.secondary, borderColor: colors.border, borderWidth: 1, borderRadius: 6, paddingHorizontal: 9, paddingVertical: 5 }}>{v}</Copy>)}</Row>; }
export function Risk({ label }: { label: string }) { const { colors } = useTheme(); return <Row style={{ alignSelf: 'flex-start', gap: 6, backgroundColor: colors.warningSoft, borderRadius: 24, paddingHorizontal: 10, paddingVertical: 7 }}><Icon name="warning" color={colors.warning} size={24} /><Copy style={{ fontSize: 20, lineHeight: 28, color: colors.warning }}>{label}</Copy></Row>; }
export function Empty({ title, subtitle }: { title: string; subtitle: string }) { return <Card style={{ alignItems: 'center', paddingVertical: 36 }}><Icon name="shield" size={30} /><Copy title style={{ fontSize: 26 }}>{title}</Copy><Copy style={{ textAlign: 'center' }}>{subtitle}</Copy></Card>; }
export function InlineError({ message }: { message?: string | null }) { return message ? <Copy accessibilityRole="alert">{message}</Copy> : null; }
const styles = StyleSheet.create({ row: { flexDirection: 'row', alignItems: 'center', gap: 12 }, title: { fontSize: 32, lineHeight: 42, fontWeight: '600', letterSpacing: -.8 }, body: { fontSize: 20, lineHeight: 30 }, card: { padding: 22, gap: 16, borderWidth: 1, borderRadius: 16 }, button: { minHeight: 64, paddingHorizontal: 20, paddingVertical: 13, borderWidth: 1, borderRadius: 30, flexDirection: 'row', gap: 12, alignItems: 'center', justifyContent: 'center' } });
