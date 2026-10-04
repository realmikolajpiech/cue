import { Stack, useLocalSearchParams } from 'expo-router';
import { useRoom } from '@/services/subtext';
import { Page } from './components';
import ProfileContent from './ProfileContent';
import { useTranslation } from '@/i18n';
export default function Person() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const room = useRoom(id);
  const { t } = useTranslation();
  return <Page><Stack.Screen options={{ title: room.data?.name ?? t('common.conversation'), headerBackTitle: t('nav.conversations') }} /><ProfileContent key={id} id={id} /></Page>;
}
