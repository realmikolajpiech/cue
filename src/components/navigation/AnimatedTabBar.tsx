import { useLayoutEffect, useState } from 'react';
import { I18nManager, StyleSheet, View, type ViewStyle } from 'react-native';
import { BottomTabBar, type BottomTabBarProps } from 'expo-router/js-tabs';
import Animated, { ReduceMotion, useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { useTheme } from '@/theme/useTheme';

const selectionSpring = { duration: 260, dampingRatio: 1, reduceMotion: ReduceMotion.System };

function TabBarBackground({ index, count, horizontalInset, topInset, bottomInset, itemInset }: {
  index: number; count: number; horizontalInset: number; topInset: number; bottomInset: number; itemInset: number;
}) {
  const { colors } = useTheme();
  const [width, setWidth] = useState(0);
  const position = useSharedValue(Math.max(index, 0));
  const slotWidth = Math.max(0, width - horizontalInset * 2) / Math.max(count, 1);
  const rtl = I18nManager.isRTL;

  useLayoutEffect(() => {
    if (index >= 0) position.set(withSpring(index, selectionSpring));
  }, [index, position]);

  const indicatorStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: (rtl ? count - 1 - position.get() : position.get()) * slotWidth }],
  }));

  return <View pointerEvents="none" style={StyleSheet.absoluteFill}
    onLayout={event => setWidth(event.nativeEvent.layout.width)}>
    {width > 0 && index >= 0 && <Animated.View style={[styles.indicator, {
      backgroundColor: colors.secondary,
      left: horizontalInset + itemInset,
      top: topInset,
      bottom: bottomInset,
      width: Math.max(0, slotWidth - itemInset * 2),
    }, indicatorStyle]} />}
  </View>;
}

// Keep the standard bar's navigation, accessibility and keyboard behaviour.
// Only its background owns motion: one opaque pill, never overlapping fades.
export function AnimatedTabBar(props: BottomTabBarProps) {
  const { state, descriptors } = props;
  const focusedRoute = state.routes[state.index];
  const descriptor = descriptors[focusedRoute.key];
  const visibleRoutes = state.routes.filter(route =>
    StyleSheet.flatten(descriptors[route.key].options.tabBarItemStyle)?.display !== 'none');
  const activeIndex = visibleRoutes.findIndex(route => route.key === focusedRoute.key);
  const barStyle = StyleSheet.flatten(descriptor.options.tabBarStyle) as ViewStyle | undefined;
  const itemStyle = StyleSheet.flatten(descriptor.options.tabBarItemStyle);
  const border = typeof barStyle?.borderWidth === 'number' ? barStyle.borderWidth : 0;
  const horizontalInset = (typeof barStyle?.paddingHorizontal === 'number' ? barStyle.paddingHorizontal : 0) + border;
  const topInset = (typeof barStyle?.paddingTop === 'number' ? barStyle.paddingTop : 0) + border;
  const bottomInset = (typeof barStyle?.paddingBottom === 'number' ? barStyle.paddingBottom : 0) + border;
  const itemInset = typeof itemStyle?.marginHorizontal === 'number' ? itemStyle.marginHorizontal : 0;

  return <BottomTabBar {...props} descriptors={{ ...descriptors, [focusedRoute.key]: {
    ...descriptor,
    options: { ...descriptor.options, tabBarBackground: () => <TabBarBackground
      index={activeIndex} count={visibleRoutes.length} horizontalInset={horizontalInset}
      topInset={topInset} bottomInset={bottomInset} itemInset={itemInset} /> },
  } }} />;
}

const styles = StyleSheet.create({
  indicator: { position: 'absolute', borderRadius: 30, borderCurve: 'continuous' },
});
