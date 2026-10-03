import { Stack, useLocalSearchParams } from 'expo-router';
import { useRoom } from '@/services/subtext';
import { Page } from './components';
import ProfileContent from './ProfileContent';
export default function Person() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const room = useRoom(id);
  return <Page><Stack.Screen options={{ title: room.data?.name ?? 'Profil' }} /><ProfileContent key={id} id={id} /></Page>;
}
