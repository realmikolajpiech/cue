import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Tabs } from 'expo-router';
import { Icon } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
export default function TabsLayout() { const { colors } = useTheme(); const insets = useSafeAreaInsets(); return <Tabs screenOptions={{ headerShown: false, tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border, maxWidth: 520, width: '100%', alignSelf: 'center', height: 92 + insets.bottom, paddingBottom: insets.bottom + 8 }, tabBarActiveTintColor: colors.text, tabBarActiveBackgroundColor: colors.secondary, tabBarInactiveTintColor: colors.secondaryText, tabBarLabelPosition: 'below-icon', tabBarLabelStyle: { fontFamily: 'DMSans', fontSize: 16, marginBottom: 3 }, tabBarItemStyle: { paddingTop: 5, borderRadius: 14, marginHorizontal: 4, marginTop: 6 } }}>
<Tabs.Screen name="index" options={{ title: 'Start', tabBarIcon: ({ color }) => <Icon name="shield" size={28} color={color} /> }} />
<Tabs.Screen name="check" options={{ title: 'Sprawdź', tabBarAccessibilityLabel: 'Sprawdź wiadomość', tabBarIcon: ({ color }) => <Icon name="scan" size={28} color={color} /> }} />
<Tabs.Screen name="alerts" options={{ title: 'Ostrzeżenia', tabBarIcon: ({ color }) => <Icon name="warning" size={28} color={color} /> }} />
</Tabs>; }
