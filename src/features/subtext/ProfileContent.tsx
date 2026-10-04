import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { Copy, Icon, Row } from '@/components/ui';
import { useRoom, useSubtextStatus, useSyncRoom } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, ui } from './components';
import { ConversationAvatar } from './ConversationRow';
import { networkName } from './conversationPresentation';
import ConversationReminders from './ConversationReminders';
import WritingStyleContent from './WritingStyleContent';
import { useTranslation } from '@/i18n';
import DemoConversation from './DemoConversation';
import ConversationAI from './ConversationAI';
import DemoName from './DemoName';
import { usePreferences } from '@/features/preferences';

export default function ProfileContent({ id }: { id: string }) {
  const query = useRoom(id);
  const room = query.data;
  const { data: status } = useSubtextStatus();
  const demoMode = usePreferences(s => s.demoMode);
  const { colors } = useTheme(); const { t } = useTranslation();
  const phase = room ? status?.[room.network].phase : undefined;
  const sync = useSyncRoom(id, phase === 'CONNECTED' && !room?.demo, room?.updatedAt);

  if (!room) return <View style={{ gap: 12 }}>
    {query.isPending ? <Copy style={ui.small}>{t('profile.opening')}</Copy> : <>
      <ErrorText error={query.error} />
      <Button label={t('common.retry')} onPress={() => { void query.refetch(); }} />
    </>}
  </View>;

  return <View style={{ gap: 8 }}>
    <Row style={styles.identity}>
      <ConversationAvatar name={room.name} uri={room.avatarUri} network={room.network} size={52} />
      <View style={{ flex: 1, gap: 2 }}>
        <Copy style={[styles.network, { color: colors.text }]}>{networkName(room.network)}</Copy>
        <Copy accessibilityLiveRegion="polite" style={styles.status}>
          {room.demo ? t('common.exampleConversation') : sync.isFetching ? t('profile.updating') : phase === 'CONNECTING' ? t('profile.connecting') : phase === 'CONNECTED' ? t('profile.connected') : t('profile.offline')}
        </Copy>
      </View>
      {!room.demo && <Pressable accessibilityRole="button" accessibilityLabel={t('profile.sync')} disabled={sync.isFetching}
        accessibilityState={{ disabled: sync.isFetching, busy: sync.isFetching }}
        onPress={() => { void sync.refetch(); }} style={({ pressed }) => [styles.iconButton, { opacity: pressed ? .6 : 1 }]}>
        {sync.isFetching ? <ActivityIndicator size="small" color={colors.accent} /> : <Icon name="refresh" size={20} color={colors.accent} />}
      </Pressable>}
    </Row>

    {sync.isError && <View style={{ gap: 4 }}>
      <Copy accessibilityRole="alert" style={[ui.small, { color: colors.warning }]}>{t('profile.syncFailed')}</Copy>
      <Pressable accessibilityRole="button" onPress={() => { void sync.refetch(); }} style={styles.retry}>
        <Copy style={[ui.small, { color: colors.accent }]}>{t('common.retry')}</Copy>
      </Pressable>
    </View>}
    <ErrorText error={query.error} />
    {demoMode && !room.demo && <DemoName key={id} id={id} />}

    {room.demo && <DemoConversation room={room} ready={!!status?.cloudEnabled && !room.aiExcluded} busy={!!status?.analyzing} />}
    <ConversationAI room={room} />
    <ConversationReminders key={id} id={id} ready={!!status?.hasApiKey && !!status.cloudEnabled && !room.aiExcluded}
      busy={!!status?.analyzing} demo={room.demo} hasMessages={!!room.messages?.length} />

    <WritingStyleContent roomId={id} isExample={room.demo} />
  </View>;
}

const styles = StyleSheet.create({
  identity: { gap: 14, paddingBottom: 20 },
  network: { fontSize: 15, lineHeight: 21, fontFamily: 'DMSansSemiBold' },
  status: { fontSize: 12, lineHeight: 18 },
  iconButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  retry: { minHeight: 44, justifyContent: 'center' },
});
