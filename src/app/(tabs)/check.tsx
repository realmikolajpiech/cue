import { t, useLanguage } from '@/i18n';
import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, Animated, Easing, Keyboard, LayoutAnimation, Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { clipboard } from '@/features/check/clipboard';
import { router } from 'expo-router';
import { Screen, Copy, Card, Row, Action, InlineError, Risk, Icon, type IconName } from '@/components/ui';
import { useTheme } from '@/theme/useTheme';
import { guardian } from '@/services/guardian';
import { useGuardianStatus } from '@/services/queries';
import { riskLabels, type ManualResult, type GuardianNotification } from '@/types/guardian';
import { analysisTitle } from '@/features/protection/presentation';
import { NotificationPicker } from '@/features/check/NotificationPicker';
import { normalizeWebsiteLink } from '@/features/check/link';

type Mode = 'notification' | 'message' | 'link';
const choices: { mode: Mode; title: string; subtitle: string; icon: IconName }[] = [
  { mode: 'notification', title: 'Powiadomienie', subtitle: 'Wybierz z historii Guardian', icon: 'bell' },
  { mode: 'message', title: 'Wiadomość', subtitle: 'Wklej lub wpisz treść', icon: 'message' },
  { mode: 'link', title: 'Link do strony', subtitle: 'Wklej adres strony', icon: 'external' },
];

export default function CheckMessage() {
  useLanguage();
  const availableChoices = choices.filter(choice => Platform.OS !== 'ios' || choice.mode !== 'notification');
  const [reduceMotion, setReduceMotion] = useState(true);
  const [formAnimation] = useState(() => new Animated.Value(0));
  const [mode, setMode] = useState<Mode | null>(null);
  const [message, setMessage] = useState('');
  const [link, setLink] = useState('');
  const [selected, setSelected] = useState<GuardianNotification | null>(null);
  const [picker, setPicker] = useState(false);
  const [focused, setFocused] = useState(false);
  const [result, setResult] = useState<ManualResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [pasting, setPasting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pending = useRef(false);
  const { colors } = useTheme();
  const { data: status } = useGuardianStatus();
  const normalizedLink = normalizeWebsiteLink(link);
  const input = mode === 'link' ? normalizedLink ?? '' : mode === 'notification' ? selected?.text ?? '' : mode === 'message' ? message.trim() : '';
  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => { if (mounted) setReduceMotion(value); }).catch(() => {});
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    return () => { mounted = false; subscription.remove(); };
  }, []);
  useEffect(() => {
    formAnimation.setValue(0);
    const animation = Animated.timing(formAnimation, { toValue: 1, duration: reduceMotion ? 0 : 280, delay: reduceMotion ? 0 : 80, easing: Easing.out(Easing.cubic), useNativeDriver: true });
    animation.start();
    return () => animation.stop();
  }, [mode, reduceMotion, formAnimation]);
  function resetResult() { setResult(null); setError(null); }
  function selectMode(next: Mode) { if (busy || (Platform.OS === 'ios' && next === 'notification')) return; if (!reduceMotion) LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut); Keyboard.dismiss(); setFocused(false); setMode(next); resetResult(); if (next === 'notification') setPicker(true); }
  function acceptPaste(text: string) {
    if (text.length > 1500) { setError('Treść jest za długa. Wklej maksymalnie 1500 znaków.'); return; }
    if (mode === 'link') setLink(text.trim()); else setMessage(text);
    resetResult();
  }
  async function paste() {
    if (pasting || busy) return;
    if (!clipboard) {
      setError('Przytrzymaj pole i wybierz Wklej. Przycisk schowka wymaga aktualizacji aplikacji.');
      return;
    }
    setPasting(true);
    try { const text = await clipboard.getStringAsync(); if (text.trim()) acceptPaste(text); else setError('Schowek jest pusty. Skopiuj wiadomość lub link.'); }
    catch { setError('Nie udało się wkleić. Przytrzymaj pole i wybierz Wklej.'); }
    finally { setPasting(false); }
  }
  function pasteButton() {
    return Platform.OS === 'ios' && clipboard?.isPasteButtonAvailable && !busy
      ? <clipboard.ClipboardPasteButton acceptedContentTypes={['plain-text']} onPress={data => { if (data.type === 'text') acceptPaste(data.text); }} backgroundColor={colors.secondary} foregroundColor={colors.text} style={{ width: 100, height: 48 }} />
      : <Pressable accessibilityRole="button" disabled={busy || pasting} accessibilityState={{ disabled: busy || pasting }} onPress={() => { void paste(); }} style={({ pressed }) => [styles.paste, { backgroundColor: colors.secondary, opacity: pressed || busy || pasting ? 0.5 : 1 }]}><Copy style={{ color: colors.text, fontFamily: 'DMSansMedium', fontSize: 17, lineHeight: 24 }}>{t(pasting ? 'Wklejam…' : 'Wklej')}</Copy></Pressable>;
  }
  async function check() {
    if (pending.current || !input) return;
    pending.current = true; setBusy(true); resetResult(); Keyboard.dismiss();
    try { setResult(await guardian.checkMessage(input)); }
    catch (cause) {
      const code = cause instanceof Error ? cause.message : '';
      setError(code.includes('model_not_ready') ? 'Model nie jest gotowy. Otwórz konfigurację Gemma i zaimportuj model.'
        : code.includes('benchmark_already_running') ? 'Trwa benchmark. Zaczekaj na jego zakończenie.'
        : code.includes('checkMessage') && code.includes('function') ? 'Ta wersja aplikacji wymaga aktualizacji buildu Android.'
        : 'Nie udało się sprawdzić wiadomości. Spróbuj ponownie lub sprawdź konfigurację modelu.');
    } finally { pending.current = false; setBusy(false); }
  }
  const label = mode === 'link' ? 'Sprawdź link' : mode === 'notification' ? 'Sprawdź powiadomienie' : mode === 'message' ? 'Sprawdź wiadomość' : 'Sprawdź';
  return <Screen footer={<Action label={busy ? t('Sprawdzam…') : t(label)} disabled={busy || !input || !status?.available || !status.modelInstalled} onPress={() => { void check(); }} />}>
    <View style={{ gap: mode ? 12 : 24 }}>
      <Copy title accessibilityRole="header" style={mode ? styles.compactTitle : styles.expandedTitle}>{t('Co chcesz sprawdzić?')}</Copy>
      <View style={[mode ? styles.modeBar : styles.choices, mode && { backgroundColor: colors.secondary }]}>
        {availableChoices.map(choice => <Pressable key={choice.mode} disabled={busy} accessibilityRole="button" accessibilityLabel={t(choice.title)} accessibilityState={{ selected: mode === choice.mode, disabled: busy }} onPress={() => selectMode(choice.mode)} style={({ pressed }) => mode
          ? [styles.modeButton, { backgroundColor: mode === choice.mode ? colors.surface : 'transparent', borderColor: mode === choice.mode ? colors.border : 'transparent', opacity: pressed || busy ? 0.6 : 1 }]
          : [styles.choice, { backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1, padding: 16, opacity: pressed || busy ? 0.6 : 1 }]}>
          <View style={mode ? styles.compactTile : [styles.tile, { backgroundColor: colors.secondary }]}><Icon name={choice.icon} size={25} color={mode && mode !== choice.mode ? colors.secondaryText : colors.text} /></View>
          {mode && <Copy style={[styles.modeLabel, { color: mode === choice.mode ? colors.text : colors.secondaryText }]}>{t(choice.title)}</Copy>}
          {!mode && <><View style={{ flex: 1, gap: 4 }}><Copy style={[styles.choiceTitle, { color: colors.text }]}>{t(choice.title)}</Copy><Copy style={styles.subtitle}>{t(choice.subtitle)}</Copy></View><Icon name="chevron" size={20} /></>}
        </Pressable>)}
      </View>
    </View>
    {mode && <Animated.View key={mode} style={{ gap: 24, opacity: formAnimation, transform: [{ translateY: formAnimation.interpolate({ inputRange: [0, 1], outputRange: [reduceMotion ? 0 : 16, 0] }) }] }}>
    {mode === 'message' && <View style={{ gap: 12 }}>
      <Row style={{ justifyContent: 'space-between' }}><Copy style={{ color: colors.text, fontFamily: 'DMSansMedium', flex: 1 }}>{t('Treść wiadomości')}</Copy>{pasteButton()}</Row>
      <TextInput accessibilityLabel={t('Treść wiadomości')} placeholder={t('Wklej lub wpisz tutaj…')} placeholderTextColor={colors.secondaryText} value={message} editable={!busy} maxLength={1500} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} onChangeText={text => { setMessage(text); resetResult(); }} multiline textAlignVertical="top" style={[styles.message, { backgroundColor: colors.surface, color: colors.text, borderColor: focused ? colors.text : colors.border }]} />
    </View>}
    {mode === 'link' && <View style={{ gap: 10 }}>
      <Copy style={{ color: colors.text, fontFamily: 'DMSansMedium' }}>{t('Adres strony')}</Copy>
      <Row style={[styles.url, { backgroundColor: colors.surface, borderColor: focused ? colors.text : colors.border }]}>
        <Icon name="external" size={23} color={colors.secondaryText} />
        <TextInput accessibilityLabel={t('Adres strony')} placeholder="np. sklep.pl" placeholderTextColor={colors.secondaryText} value={link} editable={!busy} maxLength={1500} keyboardType="url" autoCapitalize="none" autoCorrect={false} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} onChangeText={text => { setLink(text); resetResult(); }} style={[styles.link, { color: colors.text }]} />
        {pasteButton()}
      </Row>
      <Copy style={styles.subtitle} accessibilityLiveRegion="polite">{link.trim() && !normalizedLink ? t('Sprawdź adres strony, np. sklep.pl') : normalizedLink ? new URL(normalizedLink).hostname : t('Możesz wkleić adres bez https://')}</Copy>
      <Copy style={styles.subtitle}>{t('Sprawdzamy adres linku, bez otwierania strony.')}</Copy>
    </View>}
    {mode === 'notification' && selected && <Card>
      <Row><Icon name="message" /><Copy style={{ color: colors.text, fontFamily: 'DMSansMedium' }}>{selected.sourceApp}</Copy></Row>
      <Copy numberOfLines={5} style={{ color: colors.text }}>{selected.text}</Copy>
      <Action secondary label={t('Zmień powiadomienie')} onPress={() => setPicker(true)} disabled={busy} />
    </Card>}
    {busy && <Row><ActivityIndicator color={colors.text} /><Copy accessibilityLiveRegion="polite" style={{ flex: 1 }}>{t('Analizuję wiadomość na telefonie…')}</Copy></Row>}
    <InlineError message={error ? t(error) : null} />
    {mode && status && !status.available && <Copy style={styles.subtitle}>{t('Analiza wymaga buildu Guardian na Androidzie.')}</Copy>}
    {mode && status?.available && !status.modelInstalled && <Action secondary label={t('Skonfiguruj model')} onPress={() => router.push('/model-setup')} />}
    {result && <Card>{result.risk !== 'low' && <Risk label={t(riskLabels[result.risk])} />}<Copy title style={{ fontSize: 26, lineHeight: 36 }}>{t(analysisTitle(result))}</Copy>{result.risk !== 'low' && <><Copy>{result.explanation}</Copy><Copy title>{t('Co zrobić teraz?')}</Copy><Copy>{result.recommendedAction}</Copy></>}<Copy style={styles.subtitle}>{t('Analiza lokalna. AI może się pomylić.')}</Copy></Card>}
    </Animated.View>}
    {picker && Platform.OS !== 'ios' && <NotificationPicker visible onClose={() => setPicker(false)} onSelect={item => { setSelected(item); resetResult(); }} />}
  </Screen>;
}
const styles = StyleSheet.create({ expandedTitle: { fontSize: 30, lineHeight: 40 }, compactTitle: { fontSize: 20, lineHeight: 28, letterSpacing: -0.3 }, choices: { gap: 12 }, modeBar: { flexDirection: 'row', gap: 6, padding: 6, borderRadius: 20 }, modeLabel: { fontFamily: 'DMSansMedium', fontSize: 14, lineHeight: 20, textAlign: 'center' }, modeButton: { flex: 1, minHeight: 72, gap: 4, paddingHorizontal: 4, paddingVertical: 8, borderRadius: 15, borderWidth: 1, alignItems: 'center', justifyContent: 'center' }, compactTile: { alignItems: 'center', justifyContent: 'center' }, choice: { minHeight: 90, flexDirection: 'row', gap: 14, alignItems: 'center', borderRadius: 18 }, tile: { width: 46, height: 46, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, choiceTitle: { fontFamily: 'DMSansMedium', fontSize: 20, lineHeight: 27 }, subtitle: { fontSize: 16, lineHeight: 24 }, paste: { minHeight: 48, paddingHorizontal: 12, borderRadius: 12, justifyContent: 'center', alignItems: 'center' }, message: { minHeight: 190, padding: 18, borderWidth: 2, borderRadius: 18, fontFamily: 'DMSans', fontSize: 20, lineHeight: 30 }, url: { padding: 10, gap: 8, borderWidth: 2, borderRadius: 16 }, link: { flex: 1, minWidth: 0, minHeight: 48, fontFamily: 'DMSans', fontSize: 18, paddingVertical: 8 } });
