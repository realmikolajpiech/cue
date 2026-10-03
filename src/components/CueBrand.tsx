import { Image, StyleSheet, View } from 'react-native';
import { Copy } from './ui';
import { useTheme } from '@/theme/useTheme';

// Keep the supplied blue artwork as the mark; light surfaces use its lavender variant.
export function CueMark({ size = 36 }: { size?: number }) {
  const { colors } = useTheme();
  return <View style={styles.mark} accessible accessibilityLabel="Cue">
    <View style={{ width: size, height: size, borderRadius: size / 3, borderCurve: 'continuous', overflow: 'hidden' }}>
      <Image source={require('../../assets/cue-logo.png')} style={{ width: size, height: size }} accessible={false} />
    </View>
    <Copy title style={{ fontSize: size * 0.8, lineHeight: size, fontFamily: 'ManropeBold', letterSpacing: -1.2, color: colors.accent }}>cue.</Copy>
  </View>;
}

export function CueMascot({ size = 200 }: { size?: number }) {
  const { isDark } = useTheme();
  return <View style={{ width: size, height: size, maxWidth: '100%', alignSelf: 'center', borderRadius: size >= 160 ? 24 : 16, borderCurve: 'continuous', overflow: 'hidden' }}>
    <Image source={isDark ? require('../../assets/cue-logo.png') : require('../../assets/cue-mascot.png')} resizeMode="contain" accessible={false}
      style={{ width: '100%', height: '100%' }} />
  </View>;
}

export function CueCompanion({ title, text }: { title: string; text: string }) {
  const { colors } = useTheme();
  return <View style={[styles.companion, { backgroundColor: colors.mascotSurface }]}>
    <CueMascot size={84} />
    <View style={{ flex: 1, gap: 5 }}>
      <Copy title style={{ fontSize: 18, lineHeight: 25 }}>{title}</Copy>
      <Copy style={{ fontSize: 13, lineHeight: 20 }}>{text}</Copy>
    </View>
  </View>;
}

const styles = StyleSheet.create({
  mark: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  companion: { padding: 12, gap: 12, flexDirection: 'row', alignItems: 'center', borderRadius: 24, borderCurve: 'continuous' },
});
