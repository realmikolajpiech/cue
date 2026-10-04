import { Tabs } from 'expo-router';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon, type IconName } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { useTranslation } from '@/i18n';
import { AnimatedTabButton } from '@/components/navigation/AnimatedTabButton';
import { AnimatedTabBar } from '@/components/navigation/AnimatedTabBar';

function TabIcon({ name, focused }: { name: IconName; focused: boolean }) {
  const { colors } = useTheme();
  return <View style={styles.iconContainer}>
    <Icon name={name} size={23} color={focused ? colors.accent : colors.secondaryText} />
  </View>;
}

export default function TabsLayout() {
  const { colors, isDark } = useTheme();
  const insets = useSafeAreaInsets();
  const { t } = useTranslation();
  // Three peer screens stay attached so returning to the inbox doesn't reattach
  // and lay out its list during the transition.
  return <Tabs detachInactiveScreens={false} tabBar={props => <AnimatedTabBar {...props} />} screenOptions={{ headerStyle: { backgroundColor: colors.background }, headerTintColor: colors.text, headerShadowVisible: false, headerTitleStyle: { fontFamily: 'Manrope', fontSize: 24 },
    sceneStyle: { backgroundColor: colors.background },
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
    tabBarActiveBackgroundColor: 'transparent',
    tabBarInactiveBackgroundColor: 'transparent',
    tabBarLabelPosition: 'below-icon',
    tabBarIconStyle: styles.icon,
    tabBarLabelStyle: styles.label,
    tabBarItemStyle: styles.item,
    tabBarButton: props => <AnimatedTabButton {...props} />,
    animation: 'none',
    tabBarHideOnKeyboard: true }}>
    <Tabs.Screen name="index" options={{ title: t('nav.conversations'), lazy: false, headerShown: false, tabBarIcon: ({ focused }) => <TabIcon name="message" focused={focused} /> }} />
    <Tabs.Screen name="style" options={{ title: t('nav.yourStyle'), lazy: false, tabBarIcon: ({ focused }) => <TabIcon name="style" focused={focused} /> }} />
    <Tabs.Screen name="preferences" options={{ title: t('nav.settings'), lazy: false, tabBarIcon: ({ focused }) => <TabIcon name="settings" focused={focused} /> }} />
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
