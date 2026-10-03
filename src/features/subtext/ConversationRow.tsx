import { useState } from 'react';
import { Image } from 'expo-image';
import { Pressable, StyleSheet, View } from 'react-native';
import { Copy, Row } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import type { Network, Room } from '@/types/subtext';
import { messageText, conversationTime, initials, networkName } from './conversationPresentation';

const platformIcons = {
  messenger: require('../../../assets/platforms/messenger.svg'),
  whatsapp: require('../../../assets/platforms/whatsapp.svg'),
};

export function ConversationAvatar({ name, uri, size = 48, network }: { name: string; uri?: string; size?: number; network?: Network }) {
  const { colors } = useTheme();
  const [failedUri, setFailedUri] = useState<string>();
  return <View accessible={false} style={{ width: size, height: size }}>
    <View style={{ width: size, height: size, borderRadius: size / 2, overflow: 'hidden', backgroundColor: colors.secondary, alignItems: 'center', justifyContent: 'center' }}>
    <Copy style={{ fontFamily: 'DMSansSemiBold', fontSize: size * 0.32, lineHeight: size * 0.5, color: colors.accent }}>{initials(name)}</Copy>
    {!!uri && failedUri !== uri && <Image source={{ uri }} recyclingKey={uri} contentFit="cover" cachePolicy="memory"
      onError={() => setFailedUri(uri)} style={StyleSheet.absoluteFill} accessible={false} />}
    </View>
    {network && <View style={[styles.platformBadge, { backgroundColor: colors.background }]}>
      <Image source={platformIcons[network]} contentFit="contain" style={{ width: 18, height: 18 }} accessible={false} />
    </View>}
  </View>;
}

export function ConversationRow({ room, onPress }: { room: Room; onPress: () => void }) {
  const { colors } = useTheme();
  const network = networkName(room.network);
  return <Pressable accessibilityRole="button" accessibilityLabel={`${room.name}, ${network}. ${room.snippet || 'Otwórz rozmowę'}`}
    onPress={onPress} style={({ pressed }) => [styles.row, { backgroundColor: pressed ? colors.secondary : 'transparent' }]}>
    <ConversationAvatar name={room.name} uri={room.avatarUri} network={room.network} />
    <View style={styles.content}>
      <Row style={{ gap: 8, justifyContent: 'space-between' }}>
        <Copy numberOfLines={1} style={[styles.name, { color: colors.text }]}>{room.name}</Copy>
        <Copy numberOfLines={1} style={styles.time}>{conversationTime(room.updatedAt)}</Copy>
      </Row>
      <Copy numberOfLines={2} style={styles.preview}>{messageText(room.snippet) || 'Brak wiadomości'}</Copy>
    </View>
  </Pressable>;
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 80, paddingHorizontal: 20, paddingVertical: 12 },
  content: { flex: 1, gap: 4 },
  name: { flex: 1, fontSize: 16, lineHeight: 22, fontFamily: 'DMSansSemiBold' },
  preview: { fontSize: 13, lineHeight: 19 },
  time: { flexShrink: 0, fontSize: 11, lineHeight: 18, fontVariant: ['tabular-nums'] },
  platformBadge: { position: 'absolute', bottom: -2, right: -2, width: 24, height: 24, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
});
