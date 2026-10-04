import { useRef, type ComponentProps } from 'react';
import { Pressable, StyleSheet } from 'react-native';
import { Tabs } from 'expo-router';

type TabBarProps = Parameters<NonNullable<ComponentProps<typeof Tabs>['tabBar']>>[0];
type TabButtonProps = Parameters<NonNullable<TabBarProps['descriptors'][string]['options']['tabBarButton']>>[0];

export function AnimatedTabButton({
  children, style, android_ripple: _ripple, ref: _ref, onPress, onPressIn, onPressOut, ...props
}: TabButtonProps) {
  const activatedOnTouch = useRef(false);

  return <Pressable {...props}
    unstable_pressDelay={0}
    onPressIn={event => {
      onPressIn?.(event);
      // Switch on finger-down; the bar's moving pill owns all touch feedback.
      if (!activatedOnTouch.current) {
        activatedOnTouch.current = true;
        onPress?.(event);
      }
    }}
    onPressOut={event => {
      onPressOut?.(event);
      // Pressability calls onPress after onPressOut in the same event.
      // Reset afterwards, including cancelled touches and long presses.
      queueMicrotask(() => { activatedOnTouch.current = false; });
    }}
    onPress={event => {
      // Keyboard and accessibility activation still use onPress.
      // A touch already navigated on press-in, so don't emit tabPress twice.
      if (!activatedOnTouch.current) onPress?.(event);
      activatedOnTouch.current = false;
    }}
    style={[style, styles.button]}>
    {children}
  </Pressable>;
}

const styles = StyleSheet.create({
  button: { minHeight: 48, borderRadius: 30, borderCurve: 'continuous', justifyContent: 'center' },
});
