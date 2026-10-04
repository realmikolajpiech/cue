import { useMutation, useQueryClient } from '@tanstack/react-query';
import { View } from 'react-native';
import { subtext } from '@/services/subtext';
import type { Room } from '@/types/subtext';
import { ErrorText } from './components';
import { SettingsToggle } from './SettingsRows';

export default function ConversationAI({ room }: { room: Room }) {
  const client = useQueryClient();
  const mutation = useMutation({ mutationFn: (enabled: boolean) => subtext.conversationAI(room.id, enabled),
    onSuccess: () => client.invalidateQueries({ queryKey: ['subtext'] }) });
  if (!subtext.supportsConversationAI()) return null;
  return <View>
    <SettingsToggle icon="lock" title="Zezwalaj na AI w tej rozmowie" value={mutation.isPending ? mutation.variables : !room.aiExcluded}
      subtitle={room.demo ? 'Demo analizuje wyłącznie fikcyjne wiadomości, na żądanie.' : 'Przy włączonej analizie AI pamięć uzupełnia się automatycznie. Wyłącz, by wykluczyć czat także z podpowiedzi i podglądu stylu.'}
      busy={mutation.isPending} onValueChange={enabled => mutation.mutate(enabled)} />
    <ErrorText error={mutation.error} />
  </View>;
}
