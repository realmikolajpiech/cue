import { t, useLanguage } from '@/i18n/legacy';
import { useEffect, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, Pressable, StyleSheet, View } from 'react-native';
import { Copy } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';

export function ProtectionToggle({ enabled, onToggle, disabled = false }: { enabled: boolean; onToggle: () => void; disabled?: boolean }) {
  useLanguage();
  const { isDark, colors } = useTheme();
  const green = isDark ? '#6DDB98' : '#237A46';
  const [position] = useState(() => new Animated.Value(enabled ? 1 : 0));
  const [pressScale] = useState(() => new Animated.Value(1));
  const [reduceMotion, setReduceMotion] = useState(true);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted) setReduceMotion(value); }).catch(() => { if (mounted) setReduceMotion(true); });
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { mounted = false; subscription.remove(); };
  }, []);

  useEffect(() => {
    const animation = Animated.timing(position, {
      toValue: enabled ? 1 : 0,
      duration: reduceMotion ? 0 : 280,
      easing: Easing.inOut(Easing.cubic),
      useNativeDriver: true,
    });
    animation.start();
    return () => animation.stop();
  }, [enabled, position, reduceMotion]);

  function animatePress(pressed: boolean) {
    Animated.timing(pressScale, {
      toValue: pressed && !reduceMotion ? .95 : 1,
      duration: reduceMotion ? 0 : 120,
      useNativeDriver: true,
    }).start();
  }

  return <Pressable accessibilityRole="switch" accessibilityLabel={t("Ochrona wiadomości")}
    accessibilityHint={enabled ? t('Naciśnij, aby wyłączyć ochronę') : t('Naciśnij, aby włączyć ochronę')}
    accessibilityState={{ checked: enabled, disabled }} aria-checked={enabled} disabled={disabled}
    onPress={onToggle} onPressIn={() => animatePress(true)} onPressOut={() => animatePress(false)}
    style={styles.target}>
    <Animated.View style={[styles.track, { backgroundColor: isDark ? '#424A46' : '#C5CCC8', transform: [{ scale: pressScale }] }]}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.activeTrack, { backgroundColor: green, opacity: position }]} />
      <Animated.View style={[styles.thumb, { transform: [{ translateX: position.interpolate({ inputRange: [0, 1], outputRange: [0, 60] }) }] }]} />
    </Animated.View>
    <View><Copy style={{ color: colors.text, fontFamily: 'DMSansMedium', textAlign: 'center' }}>{t("Ochrona")}</Copy></View>
  </Pressable>;
}

const styles = StyleSheet.create({
  target: { alignItems: 'center', gap: 10, paddingVertical: 8, paddingHorizontal: 20, minHeight: 100 },
  track: { width: 124, height: 64, borderRadius: 32, padding: 6, overflow: 'hidden' },
  activeTrack: { borderRadius: 32 },
  thumb: { width: 52, height: 52, borderRadius: 26, backgroundColor: '#FFFFFF', boxShadow: '0px 2px 5px rgba(0, 0, 0, 0.15)' },
});
