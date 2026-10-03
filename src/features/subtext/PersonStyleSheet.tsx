import { Stack, useLocalSearchParams } from 'expo-router';
import { useRoom } from '@/services/subtext';
import ConversationMemory from './ConversationMemory';
import { usePreferences } from '@/features/preferences';
import WritingStyle from './WritingStyle';

export default function PersonStyle() {
  const { id, memory } = useLocalSearchParams<{ id: string; memory?: string }>();
  const developerMode = usePreferences(state => state.developerMode);
  const showMemory = memory === 'true' && developerMode;
  const room = useRoom(id);
  return <><Stack.Screen options={{ title: showMemory ? 'Pamięć · DEV' : 'Pamięć i styl', headerBackTitle: room.data?.name ?? 'Rozmowa' }} />
    {showMemory ? <ConversationMemory id={id} name={room.data?.name ?? 'Rozmowa'} /> : <WritingStyle roomId={id} personName={room.data?.name ?? 'tej osoby'} isExample={room.data?.demo} />}
  </>;
}
