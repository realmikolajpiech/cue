import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { KeyboardProvider } from 'react-native-keyboard-controller';
import { useTheme } from '@/theme/useTheme';
import { SubtextProvider } from '@/services/subtext';
import { useSubtextPreferences } from '@/features/subtext/preferences';
import { useAppearance } from '@/theme/preferences';
import { useLanguagePreferences } from '@/i18n/preferences';
import { useTranslation } from '@/i18n';

export const unstable_settings = { anchor: '(tabs)' };
export default function RootLayout() {
  const { isDark, colors } = useTheme();
  const appearanceReady = useAppearance(s => s.hydrated);
  const languageReady = useLanguagePreferences(s => s.hydrated);
  const { t } = useTranslation();
  const hydrated = useSubtextPreferences(s => s.hydrated); const onboarded = useSubtextPreferences(s => s.onboarded);
  const [loaded, error] = useFonts({
    DMSans: require('../../assets/fonts/DMSans-400.ttf'), DMSansMedium: require('../../assets/fonts/DMSans-500.ttf'),
    DMSansSemiBold: require('../../assets/fonts/DMSans-600.ttf'), Manrope: require('../../assets/fonts/Manrope-600.ttf'),
    ManropeBold: require('../../assets/fonts/Manrope-800.ttf'),
  });
  if ((!loaded && !error) || !hydrated || !appearanceReady || !languageReady) return null;
  return <SubtextProvider><KeyboardProvider><StatusBar style={isDark ? 'light' : 'dark'} />
    <Stack screenOptions={{ contentStyle: { backgroundColor: colors.background }, headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text, headerShadowVisible: false, headerTitleStyle: { fontFamily: 'Manrope' } }}>
      <Stack.Protected guard={onboarded}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="person/[id]" options={{ title: t('common.conversation') }} />
        <Stack.Screen name="messages/[id]" options={{ title: t('nav.messages') }} />
        <Stack.Screen name="person-style/[id]" options={{ title: t('nav.yourStyle') }} />
        <Stack.Screen name="connections" options={{ title: t('nav.connectedAccounts') }} />
        <Stack.Screen name="welcome" options={{ headerShown: false }} />
        <Stack.Screen name="settings" options={{ title: t('nav.settings') }} />
        <Stack.Screen name="settings-keyboard" options={{ title: t('nav.keyboard') }} />
        <Stack.Screen name="settings-privacy" options={{ title: t('nav.privacy') }} />
      </Stack.Protected>
      <Stack.Protected guard={!onboarded}><Stack.Screen name="onboarding" options={{ headerShown: false }} /></Stack.Protected>
    </Stack>
  </KeyboardProvider></SubtextProvider>;
}
