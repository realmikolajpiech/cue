import { useState, type ReactNode } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Switch, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { Copy, Row, Icon, Action, type IconName } from '@/components/ui';
import { LanguagePicker } from '@/components/LanguagePicker';
import { t, useLanguage } from '@/i18n';
import { useTheme } from '@/theme/useTheme';
import { guardian } from '@/services/guardian';
import { useProtection } from '@/features/protection/useProtection';
import { useDemo } from '@/features/demo/store';

function Group({ title, children }: { title: string; children: ReactNode }) {
  const { colors } = useTheme();
  return <View style={styles.section}>
    <Copy accessibilityRole="header" style={[styles.sectionTitle, { color: colors.secondaryText }]}>{title}</Copy>
    <View style={[styles.group, { backgroundColor: colors.surface, borderColor: colors.border }]}>{children}</View>
  </View>;
}

function Divider() {
  const { colors } = useTheme();
  return <View style={[styles.divider, { backgroundColor: colors.border }]} />;
}

function SettingRow({ label, icon, value, onChange, onPress, expanded, disabled = false, destructive = false }: {
  label: string; icon: IconName; value?: boolean; onChange?: () => void; onPress?: () => void;
  expanded?: boolean; disabled?: boolean; destructive?: boolean;
}) {
  const { colors } = useTheme();
  const foreground = destructive ? '#C3443B' : colors.text;
  const content = <>
    <View style={[styles.icon, { backgroundColor: colors.secondary }]}><Icon name={icon} size={19} color={foreground} /></View>
    <Copy style={[styles.rowLabel, { color: foreground }]}>{label}</Copy>
    {onChange ? <Switch accessibilityLabel={label} value={value} onValueChange={onChange} disabled={disabled}
      {...(process.env.EXPO_OS === 'web' ? { activeThumbColor: colors.surface } : {})}
      trackColor={{ false: colors.border, true: colors.text }} thumbColor={colors.surface} ios_backgroundColor={colors.border} />
      : <View style={expanded ? { transform: [{ rotate: '90deg' }] } : undefined}><Icon name="chevron" size={19} color={colors.secondaryText} /></View>}
  </>;
  if (onPress) return <Pressable accessibilityRole="button" accessibilityState={expanded === undefined ? undefined : { expanded }}
    onPress={onPress} style={({ pressed }) => [styles.row, { backgroundColor: pressed ? colors.secondary : 'transparent' }]}>{content}</Pressable>;
  return <View style={[styles.row, { opacity: disabled ? 0.5 : 1 }]}>{content}</View>;
}

export default function Settings() {
  useLanguage();
  const demo = useDemo();
  const s = useProtection();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [confirm, setConfirm] = useState(false);
  const [advanced, setAdvanced] = useState(false);
  return <View style={[styles.screen, { backgroundColor: colors.background }]}>
    <View style={styles.container}>
      <Row style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel={t('Wróć')}
          onPress={() => router.canGoBack() ? router.back() : router.replace('/')}
          style={({ pressed }) => [styles.back, { opacity: pressed ? 0.5 : 1 }]}>
          <Icon name="back" size={23} />
          <Copy style={[styles.backLabel, { color: colors.text }]}>{t('Wróć')}</Copy>
        </Pressable>
      </Row>
      <ScrollView contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + 28 }]} showsVerticalScrollIndicator={false}>
        <Row style={styles.titleRow}>
          <Copy title accessibilityRole="header" style={styles.title}>{t('Ustawienia')}</Copy>
          {!s.native && <View style={[styles.badge, { backgroundColor: colors.secondary, borderColor: colors.border }]}>
            <Copy style={styles.badgeLabel}>{t('Demo')}</Copy>
          </View>}
        </Row>
        <Group title={t('Ochrona')}>
          <SettingRow icon="shield" label={t('Ochrona wiadomości')} value={s.enabled} onChange={s.toggleProtection} disabled={s.busy} />
          <Divider />
          <SettingRow icon="bell" label={t('Pokazuj ostrzeżenia')} value={s.notifications} onChange={s.toggleNotifications} disabled={s.busy} />
        </Group>
        <Group title={t('Preferencje')}>
          <View style={styles.language}><LanguagePicker embedded /></View>
          <Divider />
          <SettingRow icon="moon" label={t('Ciemne tło')} value={demo.dark} onChange={demo.toggleTheme} />
        </Group>
        <Group title={t('Więcej')}>
          <SettingRow icon="help" label={t('Jak działa aplikacja?')} onPress={() => router.push('/onboarding')} />
          <Divider />
          <SettingRow icon="settings" label={t('Dodatkowe ustawienia')} expanded={advanced} onPress={() => setAdvanced(value => !value)} />
        </Group>
        {advanced && <>
          <Group title={t('Sprawdzane aplikacje')}>
            {Object.entries(s.apps).map(([name, value], index) => <View key={name}>
              {index > 0 && <Divider />}
              <SettingRow icon="message" label={name} value={value} disabled={s.native} onChange={() => s.toggleApp(name)} />
            </View>)}
          </Group>
          {s.native && <Group title={t('Ustawienia techniczne')}>
            <SettingRow icon="bell" label={t('Dźwięk i banery ostrzeżeń')} onPress={() => { void guardian.openWarningChannelSettings(); }} />
            <Divider />
            <SettingRow icon="shield" label={t('Model i uprawnienia')} onPress={() => router.push('/model-setup')} />
            <Divider />
            <SettingRow icon="settings" label={t('Silnik i test modelu')} onPress={() => router.push('/model-settings')} />
            <Divider />
            <SettingRow icon="history" label={t('Analizy na urządzeniu')} onPress={() => router.push('/analyses')} />
          </Group>}
          <Group title={t('Historia')}>
            <SettingRow icon="history" label={t('Usuń ostrzeżenia')} destructive onPress={() => setConfirm(true)} />
          </Group>
        </>}
        {s.error && <Copy accessibilityRole="alert" style={styles.note}>{s.error}</Copy>}
        <Row style={styles.privacy}>
          <Icon name={s.native ? 'lock' : 'help'} size={15} color={colors.secondaryText} />
          <Copy style={styles.note}>{t(s.native ? 'Wiadomości zostają na telefonie.' : 'Ustawienia ochrony dotyczą wersji demo.')}</Copy>
        </Row>
      </ScrollView>
    </View>
    <Modal visible={confirm} transparent animationType="slide" onRequestClose={() => setConfirm(false)}>
      <View style={styles.overlay}>
        <Pressable accessibilityRole="button" accessibilityLabel={t('Anuluj usuwanie')} onPress={() => setConfirm(false)} style={{ flex: 1 }} />
        <View accessibilityViewIsModal style={[styles.sheet, { paddingBottom: insets.bottom + 24, backgroundColor: colors.surface }]}>
          <ScrollView contentContainerStyle={{ gap: 18 }}>
            <Copy title style={{ fontSize: 25, lineHeight: 33 }}>{t('Usunąć ostrzeżenia?')}</Copy>
            <Copy style={{ fontSize: 16, lineHeight: 24 }}>{t(s.native ? 'Usuniesz lokalną historię analiz z telefonu.' : 'Usuniesz tylko przykładowe ostrzeżenia z tej wersji pokazowej.')}</Copy>
            <Action label={t('Tak, usuń ostrzeżenia')} disabled={s.busy} onPress={() => { void s.clear().then(() => setConfirm(false)); }} />
            <Action secondary label={t('Anuluj')} onPress={() => setConfirm(false)} />
          </ScrollView>
        </View>
      </View>
    </Modal>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  container: { width: '100%', maxWidth: 520, alignSelf: 'center', flex: 1 },
  header: { paddingHorizontal: 20, paddingBottom: 4 },
  back: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: 7, paddingRight: 12 },
  backLabel: { fontSize: 16, lineHeight: 24, fontFamily: 'DMSansMedium' },
  body: { paddingHorizontal: 20, paddingTop: 10, gap: 22 },
  titleRow: { justifyContent: 'space-between', gap: 12, marginBottom: 2 },
  title: { fontSize: 30, lineHeight: 40, flexShrink: 1 },
  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 20, borderWidth: 1 },
  badgeLabel: { fontSize: 12, lineHeight: 18, fontFamily: 'DMSansMedium' },
  section: { gap: 8 },
  sectionTitle: { paddingHorizontal: 4, fontSize: 12, lineHeight: 18, letterSpacing: 1, textTransform: 'uppercase', fontFamily: 'DMSansSemiBold' },
  group: { borderWidth: 1, borderRadius: 18, overflow: 'hidden' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 66, paddingHorizontal: 16, paddingVertical: 12 },
  icon: { width: 32, height: 32, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
  rowLabel: { flex: 1, fontSize: 16, lineHeight: 23, fontFamily: 'DMSansMedium' },
  divider: { height: StyleSheet.hairlineWidth, marginLeft: 60, marginRight: 16 },
  language: { paddingHorizontal: 16, paddingVertical: 16 },
  privacy: { alignItems: 'flex-start', justifyContent: 'center', gap: 7, paddingHorizontal: 8 },
  note: { fontSize: 12, lineHeight: 18, flexShrink: 1 },
  overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: '#00000080' },
  sheet: { maxHeight: '85%', paddingTop: 24, paddingHorizontal: 24, borderTopLeftRadius: 24, borderTopRightRadius: 24, maxWidth: 520, width: '100%', alignSelf: 'center' },
});
