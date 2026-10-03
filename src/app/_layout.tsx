import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { useTheme } from '@/theme/useTheme';
import { usePreferences } from '@/features/preferences';
import { Providers } from '@/services/queries';

void SplashScreen.preventAutoHideAsync();
export const unstable_settings = { anchor: '(tabs)' };
export default function RootLayout() {
  const { isDark, colors } = useTheme();
  const hydrated = usePreferences(s => s.hydrated);
  const onboarded = usePreferences(s => s.onboarded);
  useEffect(() => { if (hydrated) void SplashScreen.hideAsync(); }, [hydrated]);
  if (!hydrated) return null;
  return <Providers><StatusBar style={isDark ? 'light' : 'dark'} /><Stack screenOptions={{ headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text, headerShadowVisible: false, contentStyle: { backgroundColor: colors.background } }}>
    <Stack.Protected guard={!onboarded}><Stack.Screen name="onboarding" options={{ title: 'Witaj w Guardian' }} /></Stack.Protected>
    <Stack.Protected guard={onboarded}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="alert/[id]" options={{ title: 'Szczegóły analizy' }} />
    </Stack.Protected>
  </Stack></Providers>;
}
