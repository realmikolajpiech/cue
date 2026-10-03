import { useEffect, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy, Icon, Row } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { t, useLanguage } from '@/i18n';

const phrases = ['Sprawdzam, czy ta treść może być oszustwem.', 'Sprawdzanie może potrwać kilkanaście sekund.', 'Nie musisz nic naciskać. Poczekaj na wynik.', 'Wynik pojawi się tutaj automatycznie.'];

/** Shared by the real request and the developer preview; never invents progress. */
export function AnalysisPending({ preview = false, onClose }: { preview?: boolean; onClose?: () => void }) {
  useLanguage();
  const { colors, isDark } = useTheme();
  const insets = useSafeAreaInsets();
  const [motion] = useState(() => new Animated.Value(0));
  const [caption] = useState(() => new Animated.Value(1));
  const [phraseIndex, setPhraseIndex] = useState(0);
  const [reduceMotion, setReduceMotion] = useState(true);
  const accent = isDark ? '#6DDB98' : '#237A46';
  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted) setReduceMotion(value); }).catch(() => {});
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { mounted = false; subscription.remove(); };
  }, []);
  useEffect(() => {
    motion.setValue(0);
    if (reduceMotion) return;
    const animation = Animated.loop(Animated.sequence([
      Animated.timing(motion, { toValue: 1, duration: 2100, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
      Animated.delay(350),
      Animated.timing(motion, { toValue: 0, duration: 2100, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
      Animated.delay(350),
    ]));
    animation.start();
    return () => animation.stop();
  }, [motion, reduceMotion]);
  useEffect(() => {
    let mounted = true;
    caption.setValue(1);
    const timer = setInterval(() => {
      if (reduceMotion) return;
      Animated.timing(caption, { toValue: 0, duration: 260, easing: Easing.in(Easing.cubic), useNativeDriver: true }).start(({ finished }) => {
        if (!mounted || !finished) return;
        setPhraseIndex(index => (index + 1) % phrases.length);
        Animated.timing(caption, { toValue: 1, duration: 380, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
      });
    }, 4400);
    return () => { mounted = false; clearInterval(timer); caption.stopAnimation(); };
  }, [caption, reduceMotion]);
  return <Modal visible animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={() => { if (preview) onClose?.(); }}>
    <View style={[styles.screen, { backgroundColor: colors.background, paddingTop: insets.top + 20, paddingBottom: insets.bottom + 20 }]}>
      <View style={styles.container}>
        {preview && <Pressable accessibilityRole="button" accessibilityLabel={t('Zamknij podgląd')} onPress={onClose} style={styles.close}><Icon name="close" size={24} /></Pressable>}
        <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
          <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.art}>
            <View style={[styles.paper, { backgroundColor: colors.surface, borderColor: colors.border }]}>
              <Row style={{ marginBottom: 28 }}><View style={[styles.avatar, { backgroundColor: colors.secondary }]}><Icon name="message" size={22} /></View><View style={[styles.line, { width: 74, backgroundColor: colors.border }]} /></Row>
              {[150, 180, 126, 162].map((width, index) => <View key={index} style={[styles.line, { width, marginBottom: 22, backgroundColor: colors.border }]} />)}
              <Animated.View style={[styles.reader, { backgroundColor: accent, opacity: reduceMotion ? 0.15 : motion.interpolate({ inputRange: [0, 0.5, 1], outputRange: [0.08, 0.2, 0.08] }), transform: [{ translateY: motion.interpolate({ inputRange: [0, 1], outputRange: [76, 208] }) }] }]} />
              <Animated.View style={[styles.marker, { backgroundColor: accent, transform: [{ translateY: motion.interpolate({ inputRange: [0, 1], outputRange: [80, 212] }) }] }]} />
            </View>
            <View style={[styles.seal, { backgroundColor: colors.text, borderColor: colors.background }]}><Icon name="shield" size={32} color={colors.surface} /></View>
          </View>
          <View style={styles.captionFrame}>
            <Animated.View style={{ opacity: caption, transform: [{ scaleY: caption.interpolate({ inputRange: [0, 1], outputRange: [0.15, 1] }) }, { translateY: caption.interpolate({ inputRange: [0, 1], outputRange: [8, 0] }) }] }}>
              <Copy title accessibilityRole="header" style={styles.title}>{t(phrases[phraseIndex])}</Copy>
            </Animated.View>
          </View>
        </ScrollView>
      </View>
    </View>
  </Modal>;
}
const styles = StyleSheet.create({
  screen: { flex: 1, paddingHorizontal: 24 }, container: { flex: 1, width: '100%', maxWidth: 520, alignSelf: 'center' },
  close: { position: 'absolute', top: 0, right: 0, zIndex: 1, width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  body: { flexGrow: 1, justifyContent: 'center', alignItems: 'center', gap: 38, paddingVertical: 32 },
  art: { width: 252, height: 280 }, paper: { width: 236, height: 258, borderRadius: 28, borderWidth: 1, padding: 28, overflow: 'hidden', transform: [{ rotate: '-5deg' }] },
  avatar: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, line: { height: 7, borderRadius: 4 },
  reader: { position: 'absolute', top: 0, left: 18, right: 18, height: 28, borderRadius: 8 }, marker: { position: 'absolute', top: 0, left: 10, width: 3, height: 20, borderRadius: 2 },
  seal: { position: 'absolute', right: 0, bottom: 0, width: 76, height: 76, borderRadius: 26, borderWidth: 6, alignItems: 'center', justifyContent: 'center', transform: [{ rotate: '8deg' }] },
  captionFrame: { minHeight: 100, justifyContent: 'center', maxWidth: 350 },
  title: { fontSize: 32, lineHeight: 41, textAlign: 'center' },
});
