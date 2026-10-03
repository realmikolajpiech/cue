import { useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, Pressable, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { useMutation } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { usePreferences } from '@/features/preferences';
import { Card, Copy, Icon, Row } from '@/components/ui';
import { CueMascot } from '@/components/CueBrand';
import { subtext, subtextCache, useRoom, useSubtextStatus, useSyncRoom } from '@/services/subtext';
import { useTheme } from '@/theme/useTheme';
import { Button, Disclosure, ErrorText, Field, ui } from './components';
import { ConversationAvatar } from './ConversationRow';
import { conversationTime, networkName } from './conversationPresentation';
import type { Profile, Room } from '@/types/subtext';

function Evidence({ items, room }: { items: Profile['observations']; room: Room }) {
  return <View style={{ gap: 16 }}>{items.map((item, index) => <View key={index} style={{ gap: 4 }}>
    <Copy style={ui.body}>{item.text}</Copy>
    <Disclosure label="Źródła" small>{item.evidenceIds.map(id => {
      const message = room.messages?.find(m => m.id === id);
      return message ? <Copy key={id} selectable style={ui.small}>{message.isMe ? 'Ty' : message.sender || room.name}: {message.text}</Copy> : null;
    })}</Disclosure>
  </View>)}</View>;
}

export default function ProfileContent({ id }: { id: string }) {
  const developerMode = usePreferences(state => state.developerMode);
  const query = useRoom(id); const room = query.data;
  const { data: status } = useSubtextStatus(); const { colors } = useTheme();
  const phase = room ? status?.[room.network].phase : undefined;
  const sync = useSyncRoom(id, phase === 'CONNECTED' && !room?.demo);
  const draft = useRef(''); const [tab, setTab] = useState<'context' | 'reply'>('context');
  const [initialDraft, setInitialDraft] = useState('');
  function changeTab(value: 'context' | 'reply') { Keyboard.dismiss(); if (value === 'reply') setInitialDraft(draft.current); setTab(value); }
  const [replies, setReplies] = useState<Profile | null>(null);
  const [copied, setCopied] = useState<number | null>(null); const [copyError, setCopyError] = useState<unknown>();
  const analysis = useMutation({
    mutationFn: ({ draft: text }: { kind: 'context' | 'reply'; draft: string }) => subtext.analyze(id, text),
    onSuccess: (profile, request) => {
      subtextCache.setQueryData(['subtext', 'room', id], (old: Room | undefined) => old ? { ...old, profile } : old);
      if (request.kind === 'reply') { setReplies(request.draft === draft.current ? profile : null); setCopied(null); }
      void subtextCache.invalidateQueries({ queryKey: ['subtext', 'rooms'] });
    },
  });
  const profile = room?.profile;
  const busy = analysis.isPending || !!status?.analyzing;
  const ready = !!status?.hasApiKey && !!status.cloudEnabled;
  const messages = room?.messages ?? [];
  const latest = messages[messages.length - 1];
  const hasMessages = messages.length > 0;
  const anotherAnalysis = status?.analyzing && status.analyzing !== id;

  if (!room) return <View style={{ gap: 16 }}>
    {query.isPending ? <><View style={{ height: 80, backgroundColor: colors.secondary, borderRadius: 16 }} /><Copy style={ui.body}>Otwieram zapisaną rozmowę…</Copy></> :
      <><ErrorText error={query.error} /><Button label="Spróbuj ponownie" onPress={() => { void query.refetch(); }} /></>}
  </View>;

  return <View style={{ gap: 20 }}>
    <Row style={{ gap: 12 }}>
      <ConversationAvatar name={room.name} size={44} />
      <View style={{ flex: 1, gap: 2 }}><Copy style={[ui.body, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>{networkName(room.network)}</Copy>
        <Copy accessibilityLiveRegion="polite" numberOfLines={2} style={[ui.small, { minHeight: 40 }]}>{room.demo ? 'Rozmowa przykładowa' : sync.isFetching ? 'Synchronizuję wiadomości…' : phase === 'CONNECTING' ? 'Łączę komunikator…' : 'Wiadomości zapisane na telefonie'}</Copy>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel="Zsynchronizuj rozmowę" disabled={sync.isFetching || room.demo}
        onPress={() => { void sync.refetch(); }} style={styles.iconButton}>
        {sync.isFetching ? <ActivityIndicator size="small" color={colors.accent} /> : <Icon name="refresh" size={22} color={colors.accent} />}
      </Pressable>
    </Row>

    {(latest || room.snippet) && <Card style={{ padding: 16, gap: 8, borderRadius: 16 }}>
      <Row style={{ justifyContent: 'space-between', gap: 8 }}>
        <Copy style={[ui.small, { flex: 1, fontFamily: 'DMSansSemiBold' }]}>{latest ? latest.isMe ? 'Ostatnia wiadomość · Ty' : 'Ostatnia wiadomość · ' + (latest.sender || room.name) : 'Ostatnia wiadomość'}</Copy>
        <Copy style={ui.small}>{conversationTime(latest?.timestamp ?? room.updatedAt)}</Copy>
      </Row>
      <Copy selectable numberOfLines={4} style={[ui.body, { color: colors.text }]}>{latest?.text ?? room.snippet}</Copy>
    </Card>}

    {sync.isError && <View style={{ gap: 4 }}><Copy accessibilityRole="alert" style={[ui.small, { color: colors.warning }]}>Nie udało się zsynchronizować. Zapisana rozmowa jest nadal dostępna.</Copy>
      <Pressable accessibilityRole="button" onPress={() => { void sync.refetch(); }} style={{ minHeight: 44, justifyContent: 'center' }}><Copy style={[ui.small, { color: colors.accent, fontFamily: 'DMSansSemiBold' }]}>Spróbuj ponownie</Copy></Pressable>
    </View>}
    {!hasMessages && !sync.isFetching && <View style={{ gap: 12 }}>
      <Copy style={ui.body}>{room.historyNotice ?? 'Nie ma jeszcze zapisanych wiadomości.'}</Copy>
      {phase && phase !== 'CONNECTED' && phase !== 'CONNECTING' && !room.demo && <Button label="Sprawdź połączenie konta" secondary onPress={() => router.push('/connections')} />}
    </View>}
    {developerMode && <Button label="Memory · DEV" secondary onPress={() => router.push({ pathname: '/person-style/[id]', params: { id, memory: 'true' } })} />}
    <ErrorText error={query.error ?? analysis.error ?? copyError} />

    <View style={[styles.tabs, { backgroundColor: colors.secondary }]}>
      {(['context', 'reply'] as const).map(value => <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected: tab === value }}
        onPress={() => changeTab(value)} style={[styles.tab, { backgroundColor: tab === value ? colors.surface : 'transparent' }]}>
        <Copy style={{ fontSize: 15, lineHeight: 22, color: tab === value ? colors.accent : colors.secondaryText, fontFamily: 'DMSansSemiBold' }}>{value === 'context' ? 'Kontekst' : 'Odpowiedź'}</Copy>
      </Pressable>)}
    </View>

    {!ready && <View style={[styles.notice, { backgroundColor: colors.secondary }]}>
      <Copy style={[ui.body, { color: colors.text, fontFamily: 'DMSansSemiBold' }]}>Włącz pomoc Cue</Copy>
      <Copy style={ui.small}>Analiza AI przygotuje podsumowanie i propozycje odpowiedzi. W ustawieniach możesz zdecydować, czy chcesz z niej korzystać.</Copy>
      <Button label="Przejdź do ustawień" secondary onPress={() => router.push('/settings')} />
    </View>}

    {tab === 'context' ? <>
      <View style={{ gap: 12 }}>
        <Row style={{ gap: 10 }}><CueMascot size={48} /><View style={{ flex: 1, gap: 2 }}><Copy style={[ui.small, { color: colors.accent }]}>Cue podpowiada</Copy><Copy title style={ui.title}>Kontekst rozmowy</Copy></View></Row>
        {profile ? <>
          <Copy selectable style={[ui.body, { color: colors.text }]}>{profile.summary}</Copy>
          {!!profile.beforeReply && <View style={[styles.notice, { backgroundColor: colors.secondary }]}><Copy style={[ui.small, { fontFamily: 'DMSansSemiBold', color: colors.accent }]}>Przed odpowiedzią</Copy><Copy selectable style={ui.body}>{profile.beforeReply}</Copy></View>}
          <Copy style={ui.small}>Podsumowanie z {new Date(profile.createdAt).toLocaleDateString('pl-PL')}</Copy>
        </> : <Copy style={ui.body}>Cue zbierze najważniejsze tematy i ustalenia z tej rozmowy. Zacznij od krótkiego podsumowania.</Copy>}
        <Button label={analysis.isPending && analysis.variables.kind === 'context' ? 'Przygotowuję podsumowanie…' : profile ? 'Odśwież podsumowanie' : 'Podsumuj rozmowę'}
          secondary disabled={!ready || busy || !hasMessages} onPress={() => { Keyboard.dismiss(); analysis.mutate({ kind: 'context', draft: '' }); }} />
      </View>
      <Button label="Pomóż odpowiedzieć" onPress={() => changeTab('reply')} />
      {!!profile?.observations.length && <View style={[styles.details, { borderColor: colors.border }]}><Disclosure label="Styl rozmowy"><Evidence items={profile.observations} room={room} /></Disclosure></View>}
      {!!profile?.commitments.length && <View style={[styles.details, { borderColor: colors.border }]}><Disclosure label="Ustalenia"><Evidence items={profile.commitments} room={room} /></Disclosure></View>}
    </> : <>
      <View style={{ gap: 8 }}>
        <Row style={{ gap: 10 }}><CueMascot size={48} /><View style={{ flex: 1, gap: 2 }}><Copy style={[ui.small, { color: colors.accent }]}>Znajdźmy Twoje słowa</Copy><Copy title style={ui.title}>Co chcesz przekazać?</Copy></View></Row>
        <Copy style={ui.body}>Napisz krótko, jaki jest Twój cel. Cue zaproponuje odpowiedzi w Twoim stylu.</Copy>
        <Field accessibilityLabel="Cel odpowiedzi" placeholder="Np. potwierdź spotkanie i zapytaj o godzinę" multiline defaultValue={initialDraft}
          style={{ minHeight: 112, textAlignVertical: 'top' }} onChangeText={text => { draft.current = text; setReplies(null); setCopied(null); }} />
      </View>
      <Button label={analysis.isPending && analysis.variables.kind === 'reply' ? 'Przygotowuję odpowiedzi…' : replies ? 'Zaproponuj inne odpowiedzi' : 'Zaproponuj odpowiedzi'}
        disabled={!ready || busy || !hasMessages} onPress={() => { Keyboard.dismiss(); setCopyError(undefined); analysis.mutate({ kind: 'reply', draft: draft.current }); }} />
      <Copy style={ui.small}>Wybierasz i kopiujesz odpowiedź. Cue nie wysyła jej za Ciebie.</Copy>
      {replies?.suggestions.map((suggestion, index) => <Card key={replies.createdAt + '-' + index} style={{ padding: 16, gap: 12, borderRadius: 16 }}>
        <Copy style={[ui.small, { color: colors.accent, fontFamily: 'DMSansSemiBold' }]}>{suggestion.tone || 'Propozycja ' + (index + 1)}</Copy>
        <Copy selectable style={[ui.body, { color: colors.text }]}>{suggestion.text}</Copy>
        <Pressable accessibilityRole="button" accessibilityLabel={'Kopiuj odpowiedź ' + (index + 1)}
          onPress={() => { Keyboard.dismiss(); void Clipboard.setStringAsync(suggestion.text).then(() => { setCopied(index); setCopyError(undefined); }).catch(setCopyError); }}
          style={({ pressed }) => ({ minHeight: 44, justifyContent: 'center', opacity: pressed ? 0.6 : 1 })}>
          <Row style={{ gap: 8 }}><Icon name={copied === index ? 'check' : 'copy'} size={18} color={colors.accent} /><Copy accessibilityLiveRegion="polite" style={[ui.small, { color: colors.accent, fontFamily: 'DMSansSemiBold' }]}>{copied === index ? 'Skopiowano' : 'Kopiuj odpowiedź'}</Copy></Row>
        </Pressable>
      </Card>)}
    </>}
    {busy && <Row style={{ gap: 8 }}><ActivityIndicator size="small" color={colors.accent} /><Copy accessibilityLiveRegion="polite" style={[ui.small, { flex: 1 }]}>{anotherAnalysis ? 'Cue kończy analizę innej rozmowy. Za chwilę możesz spróbować tutaj.' : 'Cue czyta wiadomości i przygotowuje pomoc. Możesz wrócić do listy rozmów.'}</Copy></Row>}
    <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/messages/[id]', params: { id } })}
      style={[styles.history, { borderColor: colors.border }]}>
      <Row style={{ gap: 8 }}><Icon name="message" size={19} color={colors.accent} /><Copy style={[ui.body, { color: colors.text }]}>Zobacz wiadomości</Copy></Row><Icon name="chevron" size={19} color={colors.secondaryText} />
    </Pressable>
    <Pressable accessibilityRole="button" onPress={() => router.push({ pathname: '/person-style/[id]', params: { id } })}
      style={[styles.history, { borderColor: colors.border }]}>
      <Row style={{ gap: 8, flex: 1 }}><Icon name="style" size={19} color={colors.accent} /><Copy style={[ui.body, { color: colors.text, flexShrink: 1 }]}>Pamięć i styl tej rozmowy</Copy></Row><Icon name="chevron" size={19} color={colors.secondaryText} />
    </Pressable>
  </View>;
}
const styles = StyleSheet.create({
  tabs: { flexDirection: 'row', padding: 4, borderRadius: 16, borderCurve: 'continuous', gap: 4 },
  tab: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 12, borderCurve: 'continuous' },
  notice: { padding: 16, gap: 8, borderRadius: 16, borderCurve: 'continuous' },
  iconButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  details: { borderTopWidth: 1, paddingTop: 8 },
  history: { borderTopWidth: 1, paddingVertical: 16, minHeight: 56, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
});
