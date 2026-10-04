import { useCallback, useRef, useState } from 'react';
import { BackHandler, Pressable, StyleSheet, View } from 'react-native';
import { KeyboardAwareScrollView, type KeyboardAwareScrollViewRef } from 'react-native-keyboard-controller';
import { router, useFocusEffect } from 'expo-router';
import { useMutation } from '@tanstack/react-query';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Copy, Icon } from '@/components/ui';
import { CueMark, CueMascot } from '@/components/CueBrand';
import { useTheme } from '@/theme/useTheme';
import { Button, ErrorText, ui } from './components';
import { subtext, subtextCache, useSubtextStatus } from '@/services/subtext';
import { SettingsToggle } from './SettingsRows';
import { KeyboardSetup } from './KeyboardSettings';
import { useSubtextPreferences } from './preferences';
import { useTranslation } from '@/i18n';

// Deliberately fictional examples; cloud analysis is an explicit opt-in.
const examples = ['everyday', 'flirt', 'support'] as const;
const exampleFields = ['label', 'name', 'message', 'context', 'reply', 'desire'] as const;

export default function Onboarding({ replay = false }: { replay?: boolean }) {
  const { colors } = useTheme(); const { t } = useTranslation();
  const insets = useSafeAreaInsets();
  const finish = useSubtextPreferences(s => s.finish);
  const statusQuery = useSubtextStatus();
  const status = statusQuery.data;
  const ai = useMutation({ mutationFn: subtext.cloud, onSuccess: () => subtextCache.invalidateQueries({ queryKey: ['subtext', 'status'] }) });
  const [step, setStep] = useState(0);
  const [exampleIndex, setExampleIndex] = useState(0);
  const scroll = useRef<KeyboardAwareScrollViewRef>(null);
  const leaving = useRef(false);
  const example = Object.fromEntries(exampleFields.map(field => [field, t(`onboarding.examples.${examples[exampleIndex]}.${field}`)])) as Record<typeof exampleFields[number], string>;
  function move(next: number) {
    setStep(next);
    scroll.current?.scrollTo({ y: 0, animated: false });
  }
  useFocusEffect(useCallback(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (step === 0) return false;
      setStep(value => value - 1);
      scroll.current?.scrollTo({ y: 0, animated: false });
      return true;
    });
    return () => subscription.remove();
  }, [step]));

  function complete(connect: boolean) {
    if (leaving.current || ai.isPending) return;
    leaving.current = true;
    finish();
    router.replace(connect ? '/connections' : '/');
  }
  function dismiss() {
    if (replay && router.canGoBack()) router.back();
    else complete(false);
  }

  return <View style={[styles.screen, { backgroundColor: colors.background }]}>
    <KeyboardAwareScrollView ref={scroll} style={{ flex: 1 }} bottomOffset={24} contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag"
      contentContainerStyle={[styles.content, { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 12 }]}>
      <View style={styles.top}>
        {step > 0 ? <Pressable accessibilityRole="button" accessibilityLabel={t('onboarding.previous')} onPress={() => move(step - 1)}
          style={({ pressed }) => [styles.textAction, { opacity: pressed ? 0.5 : 1 }]}>
          <Icon name="back" /><Copy style={[ui.body, { color: colors.text }]}>{t('onboarding.back')}</Copy>
        </Pressable> : <CueMark size={32} />}
        <Pressable accessibilityRole="button" onPress={dismiss} disabled={ai.isPending} accessibilityState={{ disabled: ai.isPending }} style={({ pressed }) => [styles.textAction, { opacity: pressed || ai.isPending ? 0.5 : 1 }]}>
          <Copy style={ui.small}>{replay ? t('common.close') : t('onboarding.skip')}</Copy>
        </Pressable>
      </View>
      <View accessible accessibilityLabel={t('onboarding.step', { step: step + 1, total: 4 })} style={styles.progress}>
        {[0, 1, 2, 3].map(index => <View key={index} style={[styles.track, { backgroundColor: index <= step ? colors.accent : colors.border }]} />)}
      </View>

      <View style={styles.main}>
        <View style={styles.heading} accessibilityLiveRegion="polite">
          <Copy title accessibilityRole="header" style={ui.heading}>
            {t(`onboarding.steps.${step}.title`)}
          </Copy>
          <Copy style={ui.body}>
            {t(`onboarding.steps.${step}.body`)}
          </Copy>
        </View>

        {step === 0 && <View style={styles.example}>
          <CueMascot pose="write" size={112} />
          <View style={styles.choices} accessibilityRole="radiogroup" accessibilityLabel={t('onboarding.exampleGroup')}>
            {examples.map((item, index) => <Pressable key={item} accessibilityRole="radio"
              accessibilityState={{ checked: exampleIndex === index }} aria-checked={exampleIndex === index} onPress={() => setExampleIndex(index)}
              style={({ pressed }) => [styles.choice, { backgroundColor: exampleIndex === index ? colors.accent : colors.secondary, opacity: pressed ? 0.7 : 1 }]}>
              <Copy style={[ui.small, { fontFamily: 'DMSansSemiBold', color: exampleIndex === index ? colors.onAccent : colors.text }]}>{t(`onboarding.examples.${item}.label`)}</Copy>
            </Pressable>)}
          </View>
          <View style={[styles.bubble, { backgroundColor: colors.surface }]} accessibilityLiveRegion="polite">
            <Copy style={ui.small}>{t('onboarding.exampleConversation', { name: example.name })}</Copy>
            <Copy style={[styles.message, { color: colors.text }]}>{example.message}</Copy>
          </View>
          <Copy style={ui.body}>{example.desire}</Copy>
        </View>}

        {step === 1 && <View style={styles.example}>
          <View style={[styles.context, { borderColor: colors.border }]}>
            <View style={styles.inline}><Icon name="history" size={18} color={colors.accent} /><Copy style={[ui.small, { color: colors.accent }]}>{t('onboarding.earlier')}</Copy></View>
            <Copy style={[ui.body, { color: colors.text }]}>{example.context}</Copy>
          </View>
          <Copy style={[ui.body, { color: colors.text }]}>{example.name}: „{example.message}”</Copy>
          <View style={[styles.bubble, { backgroundColor: colors.secondary }]}>
            <Copy style={[ui.small, { color: colors.accent, fontFamily: 'DMSansSemiBold' }]}>{t('onboarding.sampleSuggestion')}</Copy>
            <Copy style={[styles.message, { color: colors.text }]}>{example.reply}</Copy>
          </View>
        </View>}

        {step === 2 && <View style={styles.example}>
          <View style={[styles.setting, { backgroundColor: colors.surface, borderColor: colors.border }]}>
            <SettingsToggle icon="message" title={t('onboarding.cloudAnalysis')}
              subtitle={!status ? t('privacy.checking') : !status.available ? t('settings.aiAndroidOnly') : status.cloudEnabled ? t('privacy.enabled') : t('settings.aiSubtitle')}
              value={ai.isPending ? ai.variables : status?.cloudEnabled ?? false}
              disabled={!status?.available} busy={ai.isPending} onValueChange={enabled => ai.mutate(enabled)} />
          </View>
          <Copy style={ui.small}>{t('onboarding.cloudOptional')}</Copy>
          <Copy style={ui.small}>{t('onboarding.cloudData')}</Copy>
          <ErrorText error={ai.error ?? statusQuery.error} />
          {statusQuery.isError && <Button label={t('common.retry')} secondary onPress={() => { void statusQuery.refetch(); }} />}
        </View>}

        {step === 3 && <View style={styles.example}>
          <KeyboardSetup />
          <Copy style={ui.small}>{t('onboarding.decide')}</Copy>
        </View>}
      </View>

      <View style={styles.footer}>
        <Button label={t(`onboarding.steps.${step}.cta`)}
          disabled={ai.isPending} onPress={() => step < 3 ? move(step + 1) : complete(true)} />
        {step === 3 && <Pressable accessibilityRole="button" onPress={() => complete(false)} style={styles.later}>
          <Copy style={ui.small}>{t('onboarding.lookAround')}</Copy>
        </Pressable>}
      </View>
    </KeyboardAwareScrollView>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  content: { flexGrow: 1, paddingHorizontal: 24, gap: 20, width: '100%', maxWidth: 520, alignSelf: 'center' },
  top: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  textAction: { minHeight: 44, minWidth: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  progress: { flexDirection: 'row', gap: 8 },
  track: { height: 4, borderRadius: 2, flex: 1 },
  main: { flexGrow: 1, gap: 24, paddingTop: 8 },
  heading: { gap: 12 },
  example: { gap: 16 },
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: { minHeight: 44, paddingVertical: 12, paddingHorizontal: 16, borderRadius: 28, borderCurve: 'continuous', justifyContent: 'center' },
  bubble: { padding: 20, borderRadius: 16, borderCurve: 'continuous', gap: 8 },
  setting: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 20, borderCurve: 'continuous', overflow: 'hidden' },
  context: { borderLeftWidth: 2, paddingLeft: 16, gap: 8 },
  inline: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  message: { fontSize: 18, lineHeight: 28 },
  footer: { gap: 4, paddingTop: 12 },
  later: { minHeight: 44, justifyContent: 'center', alignItems: 'center' },
});
