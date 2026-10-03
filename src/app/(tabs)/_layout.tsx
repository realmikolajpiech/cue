import { t, useLanguage } from '@/i18n';
import { Tabs } from 'expo-router';
import { AnimatedTabBar } from '@/components/navigation/AnimatedTabBar';

export default function TabsLayout() { useLanguage();
  return (
    <Tabs tabBar={props => <AnimatedTabBar {...props} />} screenOptions={{ headerShown: false }}>
      <Tabs.Screen name="index" options={{ title: t("Start") }} />
      <Tabs.Screen name="check" options={{ title: t("Sprawdź"), tabBarAccessibilityLabel: t("Sprawdź wiadomość") }} />
      <Tabs.Screen name="alerts" options={{ title: t("Ostrzeżenia") }} />
    </Tabs>
  );
}
