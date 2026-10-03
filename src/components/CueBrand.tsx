import { StyleSheet, View } from 'react-native';
import { Image } from 'expo-image';
import { Copy } from './ui';
import { useTheme } from '@/theme/useTheme';

const mascots = {
  wave: require('../../assets/mascots/cue-wave.png'),
  read: require('../../assets/mascots/cue-read.png'),
  write: require('../../assets/mascots/cue-write.png'),
};

export function CueMark({ size = 36, welcoming = false }: { size?: number; welcoming?: boolean }) {
  const { colors } = useTheme();
  return <View style={styles.mark} accessible accessibilityLabel="Cue">
    {welcoming ? <CueMascot pose="wave" size={size * 1.5} /> : <View style={{ width: size, height: size, borderRadius: size / 3, borderCurve: 'continuous', overflow: 'hidden' }}>
      <Image source={require('../../assets/cue-logo.png')} style={{ width: size, height: size }} accessible={false} />
    </View>}
    <Copy title style={{ fontSize: size * 0.8, lineHeight: size, fontFamily: 'ManropeBold', letterSpacing: -1.2, color: colors.accent }}>cue.</Copy>
  </View>;
}

export function CueMascot({ pose, size = 80 }: { pose: keyof typeof mascots; size?: number }) {
  return <Image source={mascots[pose]} contentFit="contain" accessible={false} pointerEvents="none"
    style={{ width: size, height: size, maxWidth: '100%', flexShrink: 0, alignSelf: 'center' }} />;
}

const styles = StyleSheet.create({
  mark: { flexDirection: 'row', alignItems: 'center', gap: 8 },
});
