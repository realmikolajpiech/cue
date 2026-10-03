import { useRef, useState } from 'react';
import { Pressable, View } from 'react-native';
import { router } from 'expo-router';
import { useMutation } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { Copy } from '@/components/ui';
import { subtext, subtextCache, useRoom, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, Disclosure, ErrorText, Field, ui } from './components';
import type { Profile, Room } from '@/types/subtext';

function Evidence({ items, room }: { items: Profile['observations']; room: Room }) {
  return <View style={{ gap: 16 }}>{items.map((item, index) => <View key={index} style={{ gap: 4 }}>
    <Copy style={ui.body}>{item.text}</Copy>
    <Disclosure label="Źródła" small>{item.evidenceIds.map(id => {
      const message = room.messages?.find(m => m.id === id);
      return message ? <Copy key={id} selectable style={ui.small}>{message.isMe ? 'Ty' : message.sender}: {message.text}</Copy> : null;
    })}</Disclosure>
  </View>)}</View>;
}

export default function ProfileContent({ id }: { id: string }) {
  const query = useRoom(id); const room = query.data; const { data: status } = useSubtextStatus(); const { colors } = useTheme();
  const draft = useRef(''); const [tab, setTab] = useState<'profile' | 'replies'>('profile');
  const [copied, setCopied] = useState<number | null>(null); const [copyError, setCopyError] = useState<unknown>();
  const analysis = useMutation({ mutationFn: () => subtext.analyze(id, draft.current), onSuccess: profile => {
    subtextCache.setQueryData(['subtext', 'room', id], (old: Room | undefined) => old ? { ...old, profile } : old);
    void subtextCache.invalidateQueries({ queryKey: ['subtext', 'rooms'] });
  } });
  const profile = room?.profile;
  const busy = analysis.isPending || !!status?.analyzing;
  const ready = status?.hasApiKey && status?.cloudEnabled;
  return <View style={{ gap: 20 }}>
    <ErrorText error={query.error ?? analysis.error ?? copyError} />
    {query.isPending && <Copy style={ui.body}>Wczytuję…</Copy>}
    {room && <>
      <View style={{ flexDirection: 'row', borderBottomWidth: 1, borderColor: colors.border }}>
        {(['profile', 'replies'] as const).map(value => <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected: tab === value }}
          onPress={() => setTab(value)} style={{ flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderBottomWidth: 2, borderBottomColor: tab === value ? colors.text : 'transparent' }}>
          <Copy style={[ui.body, { color: tab === value ? colors.text : colors.secondaryText, fontFamily: 'DMSansSemiBold' }]}>{value === 'profile' ? 'Profil' : 'Odpowiedzi'}</Copy>
        </Pressable>)}
      </View>
      {room.demo && <Copy style={ui.small}>Rozmowa przykładowa</Copy>}
      {!ready && <Button label="Skonfiguruj AI" secondary onPress={() => router.navigate('/alerts')} />}
      {tab === 'profile' ? <>
        {profile ? <>
          <Copy selectable style={[ui.body, { color: colors.text }]}>{profile.summary}</Copy>
          {!!profile.observations.length && <Disclosure label="Styl rozmowy"><Evidence items={profile.observations} room={room} /></Disclosure>}
          {!!profile.commitments.length && <Disclosure label="Ustalenia"><Evidence items={profile.commitments} room={room} /></Disclosure>}
          <Copy style={ui.small}>Aktualizacja: {new Date(profile.createdAt).toLocaleDateString('pl-PL')}</Copy>
        </> : <Copy style={ui.body}>Utwórz profil na podstawie rozmowy.</Copy>}
        <Button label={busy ? 'Tworzę profil…' : profile ? 'Odśwież profil' : 'Utwórz profil'} disabled={!ready || busy} onPress={() => analysis.mutate()} />
      </> : <>
        <Field accessibilityLabel="Co chcesz przekazać" placeholder="Co chcesz przekazać?" multiline style={{ minHeight: 80, textAlignVertical: 'top' }} onChangeText={text => { draft.current = text; }} />
        <Button label={busy ? 'Przygotowuję…' : 'Podpowiedz odpowiedź'} disabled={!ready || busy} onPress={() => { setCopied(null); analysis.mutate(); }} />
        {profile?.suggestions.map((suggestion, index) => <View key={`${profile.createdAt}-${index}`} style={{ padding: 16, gap: 8, borderRadius: 12, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border }}>
          <Copy selectable style={[ui.body, { color: colors.text }]}>{suggestion.text}</Copy>
          <Pressable accessibilityRole="button" onPress={() => { void Clipboard.setStringAsync(suggestion.text).then(() => { setCopied(index); setCopyError(undefined); }).catch(setCopyError); }} style={{ minHeight: 44, justifyContent: 'center' }}>
            <Copy style={[ui.small, { color: colors.text }]}>{copied === index ? 'Skopiowano' : 'Kopiuj'}</Copy>
          </Pressable>
        </View>)}
      </>}
      <Disclosure label="Wiadomości">
        <Button label={query.isFetching ? 'Odświeżam…' : 'Odśwież'} secondary disabled={query.isFetching} onPress={() => { void query.refetch(); }} />
        {!room.messages?.length && <Copy style={ui.small}>Brak dostępnych wiadomości.</Copy>}
        {room.messages?.map(message => <View key={message.id} style={{ gap: 4, paddingVertical: 8, borderBottomWidth: 1, borderColor: colors.border }}>
          <Copy style={ui.small}>{message.isMe ? 'Ty' : message.sender}</Copy><Copy selectable style={ui.body}>{message.text}</Copy>
        </View>)}
      </Disclosure>
    </>}
  </View>;
}
