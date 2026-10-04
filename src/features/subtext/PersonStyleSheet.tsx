import { Redirect, Stack, useLocalSearchParams } from 'expo-router';
import { useRoom } from '@/services/subtext';
import ConversationMemory from './ConversationMemory';
import { usePreferences } from '@/features/preferences';
import { useTranslation } from '@/i18n';

export default function PersonStyle() {
  const { id, memory } = useLocalSearchParams<{ id: string; memory?: string }>();
  const developerMode = usePreferences(state => state.developerMode);
  const showMemory = memory === 'true' && developerMode;
  const room = useRoom(id);
  const { t } = useTranslation();
  if (!showMemory) return <Redirect href={{ pathname: '/person/[id]', params: { id } }} />;
  return <><Stack.Screen options={{ title: t('nav.memoryDev'), headerBackTitle: room.data?.name ?? t('common.conversation') }} />
    <ConversationMemory id={id} name={room.data?.name ?? t('common.conversation')} />
  </>;
}
