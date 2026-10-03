import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, StyleSheet, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { Icon } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';

export function ProtectionShield({ enabled }: { enabled: boolean }) {
  const { isDark } = useTheme();
  const green = isDark ? '#6DDB98' : '#237A46';
  const gray = isDark ? '#A6ABA8' : '#848B87';
  const [reducedMotion, setReducedMotion] = useState<boolean | null>(null);
  const [activation] = useState(() => new Animated.Value(0));
  const [ripple] = useState(() => new Animated.Value(0));

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => {
      if (mounted) setReducedMotion(value);
    }).catch(() => { if (mounted) setReducedMotion(true); });
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    return () => { mounted = false; subscription.remove(); };
  }, []);

  // Replay on returning from settings so the activation remains visible to the user.
  useFocusEffect(useCallback(() => {
    activation.stopAnimation();
    ripple.stopAnimation();
    ripple.setValue(0);
    if (reducedMotion !== false) {
      activation.setValue(enabled ? 1 : 0);
      return;
    }
    if (enabled) activation.setValue(0);
    const transition = Animated.parallel([
      Animated.timing(activation, {
        toValue: enabled ? 1 : 0,
        duration: enabled ? 700 : 350,
        easing: Easing.inOut(Easing.cubic),
        useNativeDriver: true,
      }),
      ...(enabled ? [Animated.timing(ripple, {
        toValue: 1,
        duration: 1100,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      })] : []),
    ]);
    transition.start();
    return () => transition.stop();
  }, [enabled, reducedMotion, activation, ripple]));

  return <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" pointerEvents="none" style={styles.scene}>
    <Animated.View style={[styles.ripple, {
      borderColor: green,
      opacity: ripple.interpolate({ inputRange: [0, .25, 1], outputRange: [0, .3, 0] }),
      transform: [{ scale: ripple.interpolate({ inputRange: [0, 1], outputRange: [.7, 1.25] }) }],
    }]} />
    <Animated.View style={[styles.shield, {
      opacity: activation.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }),
    }]}><Icon name="shield" size={140} color={gray} /></Animated.View>
    <Animated.View style={[styles.shield, {
      opacity: activation,
      transform: [{ scale: activation.interpolate({ inputRange: [0, .75, 1], outputRange: [.86, 1.04, 1] }) }],
    }]}>
      <Icon name="shield" size={140} color={green} />
      <View style={styles.check}><Icon name="check" size={42} color={green} /></View>
    </Animated.View>
  </View>;
}

const styles = StyleSheet.create({
  scene: { width: 200, height: 156, alignItems: 'center', justifyContent: 'center' },
  shield: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  ripple: { position: 'absolute', width: 164, height: 164, borderRadius: 82, borderWidth: 3 },
  check: { position: 'absolute', top: 46 },
});
