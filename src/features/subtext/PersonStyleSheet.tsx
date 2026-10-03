import { Modal, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import WritingStyle from './WritingStyle';
import ConversationMemory from './ConversationMemory';
import { usePreferences } from '@/features/preferences';

export default function PersonStyleSheet({ id, name, visible, onClose, memory = false }: {
  id: string; name: string; visible: boolean; onClose: () => void; memory?: boolean;
}) {
  const developerMode = usePreferences(state => state.developerMode);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  return <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose} statusBarTranslucent>
    <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: '#00000066' }}>
      <Pressable accessibilityRole="button" accessibilityLabel="Zamknij podgląd stylu"
        onPress={onClose} style={{ flex: 1 }} />
      <View accessibilityViewIsModal style={{ height: '88%', maxWidth: 600, width: '100%', alignSelf: 'center',
        backgroundColor: colors.background, borderTopLeftRadius: 28, borderTopRightRadius: 28, overflow: 'hidden', paddingBottom: insets.bottom }}>
        <View style={{ alignItems: 'center', paddingTop: 10 }}>
          <View style={{ width: 36, height: 4, borderRadius: 2, backgroundColor: colors.border }} />
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Zamknij podgląd stylu" onPress={onClose}
          style={{ alignSelf: 'flex-end', minHeight: 44, justifyContent: 'center', paddingHorizontal: 24 }}>
          <Copy>Zamknij</Copy>
        </Pressable>
        {visible && (memory && developerMode ? <ConversationMemory id={id} name={name} /> : <WritingStyle roomId={id} personName={name} />)}
      </View>
    </View>
  </Modal>;
}
