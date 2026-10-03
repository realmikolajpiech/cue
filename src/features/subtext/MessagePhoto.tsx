import { useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { Copy } from '@/components/ui';
import { subtext } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ui } from './components';

export function MessagePhoto({ roomId, messageId }: { roomId: string; messageId: string }) {
  const { colors } = useTheme();
  const [expanded, setExpanded] = useState(false);
  const [failed, setFailed] = useState(false);
  const image = useQuery({ queryKey: ['subtext', 'image', roomId, messageId], queryFn: () => subtext.image(roomId, messageId), staleTime: Infinity, retry: false });
  if (!image.data || failed) return <View style={{ width: 240, minHeight: 100, padding: 12, gap: 8, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.secondary, borderRadius: 12 }}>
    {image.isFetching ? <><ActivityIndicator color={colors.accent} /><Copy style={ui.small}>Wczytuję zdjęcie…</Copy></> : <>
      <Copy style={ui.small}>Nie udało się wczytać zdjęcia. Komunikator mógł już usunąć dostęp do pliku.</Copy>
      <Button label="Spróbuj ponownie" secondary onPress={() => { setFailed(false); void image.refetch(); }} />
    </>}
  </View>;
  return <>
    <Pressable accessibilityRole="button" accessibilityLabel="Powiększ zdjęcie" onPress={() => setExpanded(true)}>
      <Image accessibilityLabel="Zdjęcie z rozmowy" source={{ uri: image.data }} resizeMode="contain" onError={() => setFailed(true)} style={{ width: 240, height: 220, borderRadius: 12, backgroundColor: colors.secondary }} />
    </Pressable>
    <Modal visible={expanded} onRequestClose={() => setExpanded(false)} animationType="fade">
      <View style={{ flex: 1, backgroundColor: colors.background, padding: 24, paddingTop: 64, paddingBottom: 48, gap: 16 }}>
        <Image accessibilityLabel="Powiększone zdjęcie z rozmowy" source={{ uri: image.data }} resizeMode="contain" style={{ flex: 1, width: '100%' }} />
        <Button label="Zamknij zdjęcie" onPress={() => setExpanded(false)} />
      </View>
    </Modal>
  </>;
}
