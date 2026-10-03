import { Redirect, Stack, useLocalSearchParams } from 'expo-router';
import { useRoom } from '@/services/subtext';
import ConversationMemory from './ConversationMemory';
import { usePreferences } from '@/features/preferences';

export default function PersonStyle() {
  const { id, memory } = useLocalSearchParams<{ id: string; memory?: string }>();
  const developerMode = usePreferences(state => state.developerMode);
  const showMemory = memory === 'true' && developerMode;
  const room = useRoom(id);
  if (!showMemory) return <Redirect href={{ pathname: '/person/[id]', params: { id } }} />;
  return <><Stack.Screen options={{ title: 'Pamięć · DEV', headerBackTitle: room.data?.name ?? 'Rozmowa' }} />
    <ConversationMemory id={id} name={room.data?.name ?? 'Rozmowa'} />
  </>;
}
