import { Pressable, StyleSheet, View } from 'react-native';
import { Copy, Icon } from '@/components/ui';
import { useLanguagePreferences } from '@/i18n/preferences';
import { languages, useLanguage, useTranslation } from '@/i18n';
import { useTheme } from '@/theme/useTheme';

/** Settings row with a segmented Polish / English switch. */
export function LanguagePicker() {
  const language = useLanguage();
  const setLanguage = useLanguagePreferences(state => state.setLanguage);
  const { colors } = useTheme();
  const { t } = useTranslation();
  return <View style={styles.row}>
    <View style={styles.icon}><Icon name="globe" size={21} color={colors.accent} /></View>
    <Copy style={[styles.label, { color: colors.text }]}>{t('settings.language')}</Copy>
    <View accessibilityRole="radiogroup" accessibilityLabel={t('settings.language')}
      style={[styles.segment, { backgroundColor: colors.secondary }]}>
      {languages.map(({ code, label }) => {
        const selected = language === code;
        return <Pressable key={code} accessibilityRole="radio" accessibilityLabel={label}
          accessibilityState={{ checked: selected }} onPress={() => setLanguage(code)}
          style={({ pressed }) => [styles.option, { backgroundColor: selected ? colors.surface : 'transparent', opacity: pressed ? 0.65 : 1 }]}>
          <Copy style={[styles.optionLabel, { color: selected ? colors.accent : colors.secondaryText }]}>{code.toUpperCase()}</Copy>
        </Pressable>;
      })}
    </View>
  </View>;
}

const styles = StyleSheet.create({
  row: { minHeight: 60, paddingHorizontal: 16, paddingVertical: 8, flexDirection: 'row', alignItems: 'center', gap: 12 },
  icon: { width: 24, alignItems: 'center', justifyContent: 'center' },
  label: { flex: 1, fontFamily: 'DMSansMedium', fontSize: 16, lineHeight: 22 },
  segment: { flexDirection: 'row', borderRadius: 12, borderCurve: 'continuous', padding: 3, gap: 3 },
  option: { minWidth: 48, minHeight: 38, paddingHorizontal: 10, borderRadius: 9, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  optionLabel: { fontFamily: 'DMSansSemiBold', fontSize: 14, lineHeight: 20 },
});
