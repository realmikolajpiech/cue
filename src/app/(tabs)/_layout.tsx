import { Tabs } from 'expo-router';
import { Icon } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';

export default function TabsLayout() {
  const { colors } = useTheme();
  return <Tabs screenOptions={{ headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text,
    tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border }, tabBarActiveTintColor: colors.text, tabBarInactiveTintColor: colors.secondaryText }}>
    <Tabs.Screen name="index" options={{ title: 'Profil', headerTitle: 'cue.', tabBarIcon: ({ color }) => <Icon name="message" color={color} /> }} />
    <Tabs.Screen name="check" options={{ title: 'Konta', href: null }} />
    <Tabs.Screen name="alerts" options={{ title: 'Ustawienia', tabBarIcon: ({ color }) => <Icon name="settings" color={color} /> }} />
  </Tabs>;
}
