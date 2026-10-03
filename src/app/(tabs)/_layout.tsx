import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Tabs } from 'expo-router';
import { Icon } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
export default function TabsLayout() { const { colors } = useTheme(); const insets = useSafeAreaInsets(); return <Tabs screenOptions={{ headerShown: false, tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border, maxWidth: 480, width: '100%', alignSelf: 'center', height: 72 + insets.bottom, paddingBottom: insets.bottom + 8 }, tabBarActiveTintColor: colors.text, tabBarInactiveTintColor: colors.secondaryText, tabBarLabelPosition: 'below-icon', tabBarLabelStyle: { fontFamily: 'DMSans', fontSize: 10, marginBottom: 3 }, tabBarItemStyle: { paddingTop: 5 } }}>
<Tabs.Screen name="index" options={{ title: 'Ochrona', tabBarIcon: ({ color }) => <Icon name="shield" color={color} /> }} />
<Tabs.Screen name="check" options={{ title: 'Sprawdź', tabBarAccessibilityLabel: 'Sprawdź wiadomość', tabBarIcon: ({ color }) => <Icon name="scan" color={color} /> }} />
<Tabs.Screen name="alerts" options={{ title: 'Historia', tabBarIcon: ({ color }) => <Icon name="history" color={color} /> }} />
<Tabs.Screen name="settings" options={{ title: 'Ustawienia', tabBarIcon: ({ color }) => <Icon name="settings" color={color} /> }} />
</Tabs>; }
