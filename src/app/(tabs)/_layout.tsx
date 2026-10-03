import { Tabs } from 'expo-router';
import { AnimatedTabBar } from '@/components/navigation/AnimatedTabBar';

export default function TabsLayout() {
  return (
    <Tabs tabBar={props => <AnimatedTabBar {...props} />} screenOptions={{ headerShown: false }}>
      <Tabs.Screen name="index" options={{ title: 'Start' }} />
      <Tabs.Screen name="check" options={{ title: 'Sprawdź', tabBarAccessibilityLabel: 'Sprawdź wiadomość' }} />
      <Tabs.Screen name="alerts" options={{ title: 'Ostrzeżenia' }} />
    </Tabs>
  );
}
