import { useEffect, useState, type ComponentProps } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View } from 'react-native';
import { Tabs } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon, type IconName } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';

type TabBarProps = Parameters<NonNullable<ComponentProps<typeof Tabs>['tabBar']>>[0];
const icons: Record<string, IconName> = { index: 'shield', check: 'scan', alerts: 'warning' };

export function AnimatedTabBar({ state, descriptors, navigation }: TabBarProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [width, setWidth] = useState(0);
  const [position] = useState(() => new Animated.Value(state.index));
  const slotWidth = width / state.routes.length;

  useEffect(() => {
    const animation = Animated.timing(position, {
      toValue: state.index,
      duration: 240,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    });
    animation.start();
    return () => animation.stop();
  }, [position, state.index]);

  return (
    <View style={[styles.bar, {
      backgroundColor: colors.surface,
      borderColor: colors.border,
      marginBottom: Math.max(insets.bottom, 12),
    }]}>
      <View style={styles.tabs} onLayout={event => setWidth(event.nativeEvent.layout.width)}>
        {width > 0 && <Animated.View pointerEvents="none" style={[styles.indicator, {
          backgroundColor: colors.text,
          width: slotWidth - 6,
          transform: [{ translateX: Animated.multiply(position, slotWidth) }],
        }]} />}
        {state.routes.map((route, index) => {
          const focused = state.index === index;
          const options = descriptors[route.key].options;
          const color = focused ? colors.surface : colors.secondaryText;
          return (
            <Pressable
              key={route.key}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              accessibilityLabel={options.tabBarAccessibilityLabel ?? options.title}
              testID={options.tabBarButtonTestID}
              onPress={() => {
                const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
                if (!focused && !event.defaultPrevented) navigation.navigate(route.name, route.params);
              }}
              onLongPress={() => navigation.emit({ type: 'tabLongPress', target: route.key })}
              style={({ pressed }) => [styles.tab, { opacity: pressed ? 0.75 : 1 }]}
            >
              <Icon name={icons[route.name] ?? 'shield'} size={26} color={color} />
              <Text style={[styles.label, { color }]}>{options.title ?? route.name}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    borderWidth: 1, borderRadius: 28, maxWidth: 520, width: '94%', alignSelf: 'center',
    marginTop: 8, paddingVertical: 8, paddingHorizontal: 6,
    shadowColor: '#000000', shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.03, shadowRadius: 6, elevation: 1,
  },
  tabs: { flexDirection: 'row', position: 'relative' },
  indicator: { position: 'absolute', top: 0, bottom: 0, left: 3, borderRadius: 21 },
  tab: { flex: 1, minHeight: 60, alignItems: 'center', justifyContent: 'center', gap: 3, paddingVertical: 4 },
  label: { fontFamily: 'DMSansMedium', fontSize: 16, textAlign: 'center' },
});
