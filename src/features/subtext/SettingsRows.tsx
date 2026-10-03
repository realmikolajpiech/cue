import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Switch, View } from 'react-native';
import { Copy, Icon, type IconName } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';

export function SettingsGroup({ title, children, footer }: { title: string; children: ReactNode; footer?: string }) {
  const { colors } = useTheme();
  return <View style={styles.group}>
    <Copy accessibilityRole="header" style={[styles.sectionTitle, { color: colors.secondaryText }]}>{title}</Copy>
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }]}>{children}</View>
    {!!footer && <Copy style={styles.footer}>{footer}</Copy>}
  </View>;
}

export function SettingsDivider() {
  const { colors } = useTheme();
  return <View style={{ height: StyleSheet.hairlineWidth, marginLeft: 52, backgroundColor: colors.border }} />;
}

type RowProps = { icon: IconName; title: string; subtitle?: string; disabled?: boolean; busy?: boolean };

function RowContent({ icon, title, subtitle, busy, destructive = false }: RowProps & { destructive?: boolean }) {
  const { colors } = useTheme();
  return <>
    <View style={styles.icon}>{busy ? <ActivityIndicator size="small" color={colors.accent} /> : <Icon name={icon} size={21} color={destructive ? colors.danger : colors.accent} />}</View>
    <View style={styles.copy}>
      <Copy style={[styles.label, { color: destructive ? colors.danger : colors.text }]}>{title}</Copy>
      {!!subtitle && <Copy style={styles.subtitle}>{subtitle}</Copy>}
    </View>
  </>;
}

export function SettingsRow({ onPress, destructive, expanded, ...props }: RowProps & { onPress: () => void; destructive?: boolean; expanded?: boolean }) {
  const { colors } = useTheme();
  return <Pressable accessibilityRole="button" accessibilityLabel={props.title} accessibilityHint={props.subtitle}
    accessibilityState={{ disabled: props.disabled, busy: props.busy, ...(expanded === undefined ? {} : { expanded }) }}
    disabled={props.disabled || props.busy} onPress={onPress}
    style={({ pressed }) => [styles.row, { backgroundColor: pressed ? colors.secondary : 'transparent', opacity: props.disabled ? 0.5 : 1 }]}>
    <RowContent {...props} destructive={destructive} />
    {!destructive && <View style={expanded ? { transform: [{ rotate: '90deg' }] } : undefined}><Icon name="chevron" size={18} color={colors.secondaryText} /></View>}
  </Pressable>;
}

export function SettingsToggle({ value, onValueChange, ...props }: RowProps & { value: boolean; onValueChange: (value: boolean) => void }) {
  const { colors } = useTheme();
  return <Pressable accessibilityRole="switch" accessibilityLabel={props.title} accessibilityHint={props.subtitle}
    accessibilityState={{ checked: value, disabled: props.disabled || props.busy, busy: props.busy }}
    disabled={props.disabled || props.busy} onPress={() => onValueChange(!value)}
    style={({ pressed }) => [styles.row, { backgroundColor: pressed ? colors.secondary : 'transparent', opacity: props.disabled ? 0.5 : 1 }]}>
    <RowContent {...props} />
    <View pointerEvents="none" importantForAccessibility="no-hide-descendants">
      <Switch accessible={false} value={value} disabled={props.disabled || props.busy} thumbColor={process.env.EXPO_OS === 'android' ? '#FFFFFF' : undefined} trackColor={{ false: colors.border, true: colors.accent }} />
    </View>
  </Pressable>;
}

const styles = StyleSheet.create({
  group: { gap: 8, marginBottom: 8 },
  sectionTitle: { fontFamily: 'DMSansSemiBold', fontSize: 13, lineHeight: 20, paddingHorizontal: 4 },
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 20, borderCurve: 'continuous', overflow: 'hidden' },
  row: { minHeight: 60, paddingHorizontal: 16, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  icon: { width: 24, alignItems: 'center', justifyContent: 'center' },
  copy: { flex: 1, gap: 3 },
  label: { fontFamily: 'DMSansMedium', fontSize: 16, lineHeight: 22 },
  subtitle: { fontSize: 12, lineHeight: 18 },
  footer: { fontSize: 12, lineHeight: 18, paddingHorizontal: 4 },
});
