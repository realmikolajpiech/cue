import { useRef, useState } from 'react';
import { Pressable, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useMutation } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { Action, Card, Copy, Row } from '@/components/ui';
import { subtext, subtextCache, useRoom, useSubtextStatus } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { ErrorText, Field, Intro, ui } from './components';
import type { Profile, Room } from '@/types/subtext';

function Evidence({ item, room }: { item: Profile['observations'][number]; room: Room }) {
  const [open, setOpen] = useState(false); const { colors } = useTheme();
  return <View style={{ gap: 8 }}><Copy style={ui.body}>{item.text}</Copy>
    <Pressable accessibilityRole="button" accessibilityState={{ expanded: open }} onPress={() => setOpen(v => !v)} style={{ minHeight: 44, justifyContent: 'center' }}>
      <Copy style={[ui.small, { color: colors.text, textDecorationLine: 'underline' }]}>{open ? 'Ukryj wiadomości' : `Pokaż źródła (${item.evidenceIds.length})`}</Copy>
    </Pressable>
    {open && item.evidenceIds.map(id => { const message = room.messages?.find(m => m.id === id); return <View key={id} style={{ padding: 12, borderLeftWidth: 2, borderColor: colors.border }}>
      <Copy style={ui.small}>{message ? `${message.isMe ? 'Ty' : message.sender} · ${new Date(message.timestamp).toLocaleDateString('pl-PL')}` : 'Wiadomość poza zachowaną historią'}</Copy>
      {message && <Copy selectable style={ui.body}>{message.text}</Copy>}
    </View>; })}
  </View>;
}

export default function Person() {
  const params = useLocalSearchParams<{ id: string }>(); const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const roomQuery = useRoom(id); const room = roomQuery.data; const { data: status } = useSubtextStatus(); const { colors } = useTheme();
  const draft = useRef(''); const [copied, setCopied] = useState<number | null>(null); const [copyError, setCopyError] = useState<unknown>();
  const analysis = useMutation({ mutationFn: () => subtext.analyze(id, draft.current), onSuccess: profile => {
    subtextCache.setQueryData(['subtext', 'room', id], (old: Room | undefined) => old ? { ...old, profile } : old);
    void subtextCache.invalidateQueries({ queryKey: ['subtext', 'rooms'] });
  } });
  const profile = room?.profile;
  return <View style={{ flex: 1, backgroundColor: colors.background }}><Stack.Screen options={{ title: room?.name ?? 'Rozmowa' }} />
    <FlashList data={room?.messages ?? []} keyExtractor={item => item.id} contentContainerStyle={{ padding: 24, paddingBottom: 40 }}
      ListHeaderComponent={<View style={{ gap: 24, marginBottom: 24 }}>
        <Intro eyebrow={room?.network === 'whatsapp' ? 'WhatsApp' : 'Messenger'} title={room?.name ?? (roomQuery.isPending ? 'Wczytuję…' : 'Rozmowa')} text={room?.kind === 'GROUP' ? 'Kontekst rozmowy grupowej' : 'Pamięć komunikacji z tą osobą'} />
        <ErrorText error={roomQuery.error} />
        {room && <>
          {room.demo && <Copy style={ui.small}>Przykład demonstracyjny — fikcyjne osoby i wiadomości. Analiza korzysta z prawdziwego API DeepSeek.</Copy>}
          <Copy style={ui.small}>{room.messages?.length ?? 0} zachowanych wiadomości · AI może się mylić. Sprawdzaj źródła.</Copy>
          <Field accessibilityLabel="Co chcesz odpowiedzieć" placeholder="Co chcesz przekazać? (opcjonalnie)" multiline style={{ minHeight: 96, textAlignVertical: 'top' }} onChangeText={text => { draft.current = text; }} />
          {!status?.cloudEnabled && <Copy style={ui.body}>Włącz analizę w DeepSeek w ustawieniach, aby otrzymać profil i podpowiedzi.</Copy>}
          <Action label={analysis.isPending || status?.analyzing === id ? 'Analizuję rozmowę…' : profile ? 'Odśwież profil i odpowiedzi' : 'Poznaj kontekst i odpowiedzi'}
            disabled={analysis.isPending || !!status?.analyzing || !status?.cloudEnabled || !status?.hasApiKey} onPress={() => { setCopied(null); analysis.mutate(); }} />
          <ErrorText error={analysis.error ?? copyError} />
          {profile && <>
            <Card><Copy style={ui.eyebrow}>Zanim odpowiesz</Copy><Copy style={ui.title}>{profile.beforeReply}</Copy><Copy style={ui.body}>{profile.summary}</Copy>
              <Copy style={ui.small}>Na podstawie {profile.messageCount} wiadomości · {new Date(profile.createdAt).toLocaleString('pl-PL')}</Copy>
            </Card>
            <Copy title style={ui.title}>Możesz odpowiedzieć</Copy>
            {profile.suggestions.map((suggestion, index) => <Card key={`${profile.createdAt}-${index}`}><Copy style={ui.eyebrow}>{suggestion.tone}</Copy><Copy selectable style={ui.body}>{suggestion.text}</Copy>
              <Action secondary label={copied === index ? 'Skopiowano' : 'Kopiuj odpowiedź'} onPress={() => { void Clipboard.setStringAsync(suggestion.text).then(() => { setCopied(index); setCopyError(undefined); }).catch(setCopyError); }} />
            </Card>)}
            {profile.observations.length > 0 && <Card><Copy style={ui.title}>Wzorce komunikacji</Copy>{profile.observations.map((item, index) => <Evidence key={index} item={item} room={room} />)}</Card>}
            {profile.commitments.length > 0 && <Card><Copy style={ui.title}>Ustalenia do zapamiętania</Copy>{profile.commitments.map((item, index) => <Evidence key={index} item={item} room={room} />)}</Card>}
          </>}
          <Row style={{ justifyContent: 'space-between' }}><Copy title style={ui.title}>Dostępne wiadomości</Copy><Pressable accessibilityRole="button" accessibilityLabel="Odśwież wiadomości" onPress={() => { void roomQuery.refetch(); }} style={{ minHeight: 44, justifyContent: 'center' }}><Copy style={ui.small}>Odśwież</Copy></Pressable></Row>
        </>}
      </View>}
      ListEmptyComponent={room ? <Copy style={ui.body}>Nie ma jeszcze dostępnych wiadomości. Spróbuj odświeżyć po synchronizacji komunikatora.</Copy> : null}
      renderItem={({ item }) => <View style={{ marginBottom: 12, padding: 16, maxWidth: '94%', alignSelf: item.isMe ? 'flex-end' : 'flex-start', backgroundColor: item.isMe ? colors.secondary : colors.surface, borderRadius: 16, borderCurve: 'continuous', gap: 6 }}>
        <Copy style={ui.small}>{item.isMe ? 'Ty' : item.sender || room?.name} · {new Date(item.timestamp).toLocaleString('pl-PL', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</Copy>
        <Copy selectable style={[ui.body, { color: colors.text }]}>{item.text}</Copy>
      </View>} />
  </View>;
}
