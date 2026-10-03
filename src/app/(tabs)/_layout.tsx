import { router, Tabs } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon, type IconName } from '@/components/ui';
import { CueMark } from '@/components/CueBrand';
import { useTheme } from '@/theme/useTheme';

function TabIcon({ name, focused }: { name: IconName; focused: boolean }) {
  const { colors } = useTheme();
  return <View style={styles.iconContainer}>
    <Icon name={name} size={23} color={focused ? colors.accent : colors.secondaryText} />
  </View>;
}

export default function TabsLayout() {
  const { colors, isDark } = useTheme();
  const insets = useSafeAreaInsets();
  return <Tabs screenOptions={{ headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text, headerShadowVisible: false, headerTitleStyle: { fontFamily: 'Manrope', fontSize: 24 },
    tabBarStyle: [styles.bar, {
      backgroundColor: colors.surface,
      borderColor: isDark ? '#424B65' : '#FFFFFF',
      marginBottom: Math.max(insets.bottom, 12),
      marginLeft: Math.max(insets.left, 24),
      marginRight: Math.max(insets.right, 24),
      boxShadow: isDark ? '0 8px 24px rgba(0, 0, 0, 0.24)' : '0 8px 24px rgba(36, 40, 63, 0.09), 0 1px 3px rgba(36, 40, 63, 0.04)',
    }],
    tabBarActiveTintColor: colors.accent,
    tabBarInactiveTintColor: colors.secondaryText,
    tabBarActiveBackgroundColor: colors.secondary,
    tabBarLabelPosition: 'below-icon',
    tabBarIconStyle: styles.icon,
    tabBarLabelStyle: styles.label,
    tabBarItemStyle: styles.item,
    tabBarButton: ({ android_ripple: _ripple, ref: _ref, ...props }) => <Pressable {...props}
      style={({ pressed }) => [props.style, { opacity: pressed ? 0.65 : 1 }]} />,
    animation: 'none',
    tabBarHideOnKeyboard: true }}>
    <Tabs.Screen name="index" options={{ title: 'Rozmowy', headerTitle: () => <CueMark size={36} />, tabBarIcon: ({ focused }) => <TabIcon name="message" focused={focused} />, headerRight: () =>
      <Pressable accessibilityRole="button" accessibilityLabel="Połączone konta" onPress={() => router.push('/connections')} style={({ pressed }) => ({ width: 44, height: 44, marginRight: 12, alignItems: 'center', justifyContent: 'center', opacity: pressed ? 0.5 : 1 })}><Icon name="accounts" size={24} color={colors.accent} /></Pressable> }} />
    <Tabs.Screen name="style" options={{ title: 'Twój styl', tabBarIcon: ({ focused }) => <TabIcon name="style" focused={focused} /> }} />
    <Tabs.Screen name="check" options={{ title: 'Konta', href: null }} />
    <Tabs.Screen name="alerts" options={{ title: 'Ustawienia', tabBarIcon: ({ focused }) => <TabIcon name="settings" focused={focused} /> }} />
  </Tabs>;
}

const styles = StyleSheet.create({
  bar: {
    height: 72,
    marginTop: 8,
    paddingTop: 6,
    paddingBottom: 6,
    paddingHorizontal: 6,
    borderWidth: StyleSheet.hairlineWidth,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderRadius: 36,
    borderCurve: 'continuous',
    elevation: 0,
  },
  item: { marginHorizontal: 2, borderRadius: 30, borderCurve: 'continuous', overflow: 'hidden' },
  icon: { width: 28, height: 28 },
  iconContainer: {
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    fontFamily: 'DMSansSemiBold',
    fontSize: 11,
    lineHeight: 16,
    marginTop: 2,
  },
});
