import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { spacing, useTheme } from '@/theme/useTheme';

export default function HomeScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <ScrollView
      style={{ backgroundColor: colors.background }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[
        styles.content,
        { paddingBottom: insets.bottom + spacing.lg },
      ]}
    >
      <Text style={[styles.eyebrow, { color: colors.accent }]}>
        FUNDAMENT PROJEKTU
      </Text>
      <Text style={[styles.title, { color: colors.text }]}>
        Prywatna ochrona zaczyna się tutaj.
      </Text>
      <Text style={[styles.body, { color: colors.secondaryText }]}>
        Projekt Expo jest gotowy do dalszej pracy. Funkcje Guardian będą
        implementowane w kolejnych zadaniach.
      </Text>
      <View
        style={[
          styles.panel,
          { backgroundColor: colors.surface, borderColor: colors.border },
        ]}
      >
        <Text style={[styles.panelTitle, { color: colors.text }]}>
          Ochrona nie jest jeszcze aktywna
        </Text>
        <Text style={[styles.body, { color: colors.secondaryText }]}>
          Ten szkielet nie odczytuje powiadomień i nie analizuje wiadomości.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, gap: spacing.lg },
  eyebrow: { fontSize: 12, fontWeight: '700', letterSpacing: 1 },
  title: { fontSize: 32, lineHeight: 40, fontWeight: '700' },
  body: { fontSize: 16, lineHeight: 24 },
  panel: {
    padding: spacing.md,
    borderWidth: 1,
    borderRadius: 16,
    borderCurve: 'continuous',
    gap: spacing.sm,
  },
  panelTitle: { fontSize: 17, lineHeight: 24, fontWeight: '600' },
});
