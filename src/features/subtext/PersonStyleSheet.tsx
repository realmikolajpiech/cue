import { Stack, useLocalSearchParams } from 'expo-router';
import { useRoom } from '@/services/subtext';
import WritingStyle from './WritingStyle';

export default function PersonStyle() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const room = useRoom(id);
  return <><Stack.Screen options={{ title: 'Pamięć i styl', headerBackTitle: room.data?.name ?? 'Rozmowa' }} />
    <WritingStyle roomId={id} personName={room.data?.name ?? 'tej osoby'} />
  </>;
}
