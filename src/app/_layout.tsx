import { usePreferences } from '@/features/preferences';
import { t, useLanguage } from '@/i18n';
import { Providers } from '@/services/queries';
import { useFonts } from 'expo-font';
import { router, Stack } from 'expo-router';
import { Pressable } from 'react-native';
import { Copy } from '@/components/ui';
import { StatusBar } from 'expo-status-bar';
import { useTheme } from '@/theme/useTheme';

export const unstable_settings = { anchor: '(tabs)' };

export default function RootLayout() {
  useLanguage();
  const { isDark, colors } = useTheme();
  const hydrated = usePreferences(s => s.hydrated);
  const onboarded = usePreferences(s => s.onboarded);
  const onboardingOpen = usePreferences(s => s.onboardingOpen);
  const [loaded, error] = useFonts({
    DMSans: require('../../assets/fonts/DMSans-400.ttf'),
    DMSansMedium: require('../../assets/fonts/DMSans-500.ttf'),
    DMSansSemiBold: require('../../assets/fonts/DMSans-600.ttf'),
    Manrope: require('../../assets/fonts/Manrope-600.ttf'),
    ManropeBold: require('../../assets/fonts/Manrope-800.ttf'),
  });
  if ((!loaded && !error) || !hydrated) return null;
  return <Providers>
    <StatusBar style={isDark ? 'light' : 'dark'} />
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <Stack.Protected guard={onboarded}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="alert/[id]" options={{
          presentation: 'modal', headerShown: true, title: t('Alerty'),
          headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text,
          headerShadowVisible: false, headerBackVisible: false,
          headerRight: () => <Pressable accessibilityRole="button" accessibilityLabel={t('Zamknij')}
            onPress={() => router.canGoBack() ? router.back() : router.replace('/alerts')}
            style={({ pressed }) => ({ minHeight: 48, minWidth: 80, justifyContent: 'center', alignItems: 'center', opacity: pressed ? 0.6 : 1 })}>
            <Copy style={{ color: colors.text, fontSize: 18, fontFamily: 'DMSansMedium' }}>{t('Zamknij')}</Copy>
          </Pressable>,
        }} />
        <Stack.Screen name="model-setup" options={{ headerShown: true, title: t('Model i dostęp') }} />
        <Stack.Screen name="model-settings" options={{ headerShown: true, title: t('Silnik Gemma') }} />
        <Stack.Screen name="analyses" options={{ headerShown: true, title: t('Analizy na urządzeniu') }} />
        <Stack.Screen name="analysis/[id]" options={{ headerShown: true, title: t('Szczegóły analizy') }} />
        <Stack.Screen name="settings" />
        <Stack.Screen name="help" options={{ presentation: 'modal' }} />
      </Stack.Protected>
      <Stack.Protected guard={!onboarded || onboardingOpen}>
        <Stack.Screen name="onboarding" options={{ presentation: onboarded ? 'modal' : 'card' }} />
      </Stack.Protected>
    </Stack>
  </Providers>;
}
