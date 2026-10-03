import { useState } from 'react';
import { Image } from 'expo-image';
import { Pressable, StyleSheet, View } from 'react-native';
import { Copy, Icon, Row } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import type { Room } from '@/types/subtext';
import { conversationTime, initials, networkName } from './conversationPresentation';

export function ConversationAvatar({ name, uri, size = 48 }: { name: string; uri?: string; size?: number }) {
  const { colors } = useTheme();
  const [failedUri, setFailedUri] = useState<string>();
  return <View accessible={false} style={{ width: size, height: size, borderRadius: size / 2, overflow: 'hidden', backgroundColor: colors.secondary, alignItems: 'center', justifyContent: 'center' }}>
    <Copy style={{ fontFamily: 'DMSansSemiBold', fontSize: size * 0.32, lineHeight: size * 0.5, color: colors.accent }}>{initials(name)}</Copy>
    {!!uri && failedUri !== uri && <Image source={{ uri }} recyclingKey={uri} contentFit="cover" cachePolicy="memory"
      onError={() => setFailedUri(uri)} style={StyleSheet.absoluteFill} accessible={false} />}
  </View>;
}

export function ConversationRow({ room, onPress }: { room: Room; onPress: () => void }) {
  const { colors } = useTheme();
  const network = networkName(room.network);
  return <Pressable accessibilityRole="button" accessibilityLabel={`${room.name}, ${network}. ${room.snippet || 'Otwórz rozmowę'}`}
    onPress={onPress} style={({ pressed }) => [styles.row, { backgroundColor: pressed ? colors.secondary : colors.surface }]}>
    <ConversationAvatar name={room.name} uri={room.avatarUri} />
    <View style={styles.content}>
      <Row style={{ gap: 8, justifyContent: 'space-between' }}>
        <Copy numberOfLines={1} style={[styles.name, { color: colors.text }]}>{room.name}</Copy>
        <Copy style={styles.time}>{conversationTime(room.updatedAt)}</Copy>
      </Row>
      <Copy numberOfLines={1} style={styles.preview}>{room.snippet || 'Otwórz, aby poznać kontekst rozmowy'}</Copy>
      <Row style={{ gap: 4 }}><Icon name="message" size={11} color={colors.secondaryText} /><Copy style={styles.network}>{network}</Copy></Row>
    </View>
    <Icon name="chevron" size={16} color={colors.secondaryText} />
  </Pressable>;
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 96, paddingHorizontal: 16, paddingVertical: 14 },
  content: { flex: 1, gap: 4 },
  name: { flex: 1, fontSize: 16, lineHeight: 22, fontFamily: 'DMSansSemiBold' },
  preview: { fontSize: 14, lineHeight: 20 },
  time: { fontSize: 11, lineHeight: 18 },
  network: { fontSize: 11, lineHeight: 16 },
});
