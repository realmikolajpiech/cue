import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useTheme } from '@/theme/useTheme';
export const unstable_settings = { anchor: '(tabs)' };
export default function RootLayout() { const { isDark, colors } = useTheme(); const [loaded, error] = useFonts({ DMSans: require('../../assets/fonts/DMSans-400.ttf'), DMSansMedium: require('../../assets/fonts/DMSans-500.ttf'), DMSansSemiBold: require('../../assets/fonts/DMSans-600.ttf'), Manrope: require('../../assets/fonts/Manrope-600.ttf'), ManropeBold: require('../../assets/fonts/Manrope-800.ttf') }); if (!loaded && !error) return null; return <><StatusBar style={isDark ? 'light' : 'dark'} /><Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}><Stack.Screen name="(tabs)" /><Stack.Screen name="alert/[id]" options={{ presentation: 'modal', headerShown: true, title: 'Szczegóły ostrzeżenia', headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text, headerShadowVisible: false }} /><Stack.Screen name="onboarding" options={{ presentation: 'modal' }} /></Stack></>; }
