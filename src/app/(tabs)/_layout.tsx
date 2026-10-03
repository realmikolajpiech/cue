import { Tabs } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useTheme } from '@/theme/useTheme';

export default function TabsLayout() {
  const { colors } = useTheme();
  return <Tabs screenOptions={{ headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text, headerShadowVisible: false, tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border }, tabBarActiveTintColor: colors.accent, tabBarInactiveTintColor: colors.secondaryText }}>
    <Tabs.Screen name="index" options={{ title: 'Ochrona', tabBarIcon: ({ color }) => <SymbolView name={{ ios: 'shield', android: 'shield', web: 'shield' }} tintColor={color} size={24} /> }} />
    <Tabs.Screen name="alerts" options={{ title: 'Analizy', tabBarIcon: ({ color }) => <SymbolView name={{ ios: 'list.bullet', android: 'list', web: 'list' }} tintColor={color} size={24} /> }} />
    <Tabs.Screen name="settings" options={{ title: 'Ustawienia', tabBarIcon: ({ color }) => <SymbolView name={{ ios: 'gearshape', android: 'settings', web: 'settings' }} tintColor={color} size={24} /> }} />
  </Tabs>;
}
