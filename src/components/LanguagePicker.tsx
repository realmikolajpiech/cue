import { Pressable, StyleSheet, View } from 'react-native';
import { Card, Copy, Row, Icon } from '@/components/ui';
import { usePreferences } from '@/features/preferences';
import { languages, t, useLanguage } from '@/i18n';
import { useTheme } from '@/theme/useTheme';

export function LanguagePicker({ embedded = false }: { embedded?: boolean }) {
  const language = useLanguage();
  const setLanguage = usePreferences(state => state.setLanguage);
  const { colors } = useTheme();
  const content = <View style={styles.content}>
    <Copy style={[styles.label, { color: colors.text }]}>{t('Język aplikacji')}</Copy>
    <View accessibilityRole="radiogroup" accessibilityLabel={t('Język aplikacji')}
      style={[styles.segment, { backgroundColor: colors.secondary }]}>
      {languages.map(({ code, label }) => <Pressable key={code} accessibilityRole="radio"
        accessibilityLabel={label} accessibilityState={{ checked: language === code }}
        onPress={() => setLanguage(code)}
        style={({ pressed }) => [styles.option, {
          backgroundColor: language === code ? colors.surface : 'transparent',
          borderColor: language === code ? colors.border : 'transparent', opacity: pressed ? 0.65 : 1,
        }]}>
        <Row style={styles.optionContent}>
          <Copy style={[styles.optionLabel, { color: language === code ? colors.text : colors.secondaryText }]}>{label}</Copy>
          {language === code && <Icon name="check" size={16} />}
        </Row>
      </Pressable>)}
    </View>
  </View>;
  return embedded ? content : <Card style={styles.card}>{content}</Card>;
}

const styles = StyleSheet.create({
  card: { padding: 18 },
  content: { gap: 12 },
  label: { fontFamily: 'DMSansMedium', fontSize: 17, lineHeight: 24 },
  segment: { flexDirection: 'row', borderRadius: 12, padding: 3, gap: 3 },
  option: { flex: 1, minHeight: 44, paddingHorizontal: 8, paddingVertical: 9, borderRadius: 9, borderWidth: 1, justifyContent: 'center' },
  optionContent: { justifyContent: 'center', gap: 6, flexWrap: 'wrap' },
  optionLabel: { fontFamily: 'DMSansMedium', fontSize: 15, lineHeight: 22 },
});
