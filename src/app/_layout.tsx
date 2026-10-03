import { useFonts } from 'expo-font';
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
  const [fontsLoaded, fontError] = useFonts({
    DMSans: require('../../assets/fonts/DMSans-400.ttf'),
    DMSansMedium: require('../../assets/fonts/DMSans-500.ttf'),
    DMSansSemiBold: require('../../assets/fonts/DMSans-600.ttf'),
    Manrope: require('../../assets/fonts/Manrope-600.ttf'),
    ManropeBold: require('../../assets/fonts/Manrope-800.ttf'),
  });
  const native = process.env.EXPO_OS === 'android';
  const fontsReady = fontsLoaded || !!fontError;
  const hydrated = usePreferences(s => s.hydrated);
  const onboarded = usePreferences(s => s.onboarded);
  useEffect(() => { if (fontsReady && (!native || hydrated)) void SplashScreen.hideAsync(); }, [fontsReady, hydrated, native]);
  if (!fontsReady || (native && !hydrated)) return null;
  return <Providers><StatusBar style={isDark ? 'light' : 'dark'} /><Stack screenOptions={{ headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text, headerShadowVisible: false, contentStyle: { backgroundColor: colors.background } }}>
    <Stack.Protected guard={native && !onboarded}><Stack.Screen name="onboarding" options={{ title: 'Witaj w Guardian' }} /></Stack.Protected>
    <Stack.Protected guard={!native || onboarded}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="alert/[id]" options={{ title: 'Szczegóły analizy', presentation: native ? 'card' : 'modal' }} />
      {!native && <Stack.Screen name="onboarding" options={{ presentation: 'modal', headerShown: false }} />}
    </Stack.Protected>
  </Stack></Providers>;
}
