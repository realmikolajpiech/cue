import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, BackHandler, Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Linking from 'expo-linking';
import { Action, Card, Copy, Icon, InlineError, Row, type IconName } from '@/components/ui';
import { usePreferences } from '@/features/preferences';
import { guardian } from '@/services/guardian';
import { useAction, useGuardianStatus } from '@/services/queries';
import { useTheme } from '@/theme/useTheme';
import model from '../../../models/catalog/gemma3-1b.json';
import { StepTransition } from './StepTransition';

const steps: { title: string; icon: IconName }[] = [
  { title: 'Chroń swoje pieniądze', icon: 'shield' },
  { title: 'Sprawdzaj nowe wiadomości', icon: 'message' },
  { title: 'Nie przegap ostrzeżenia', icon: 'bell' },
  { title: 'Włącz ochronę', icon: 'shield' },
];
const downloadErrors: Record<string, string> = {
  model_insufficient_space: 'Brakuje miejsca na telefonie. Zwolnij co najmniej 650 MB i spróbuj ponownie.',
  model_initialization_failed: 'Nie udało się przygotować ochrony. Zamknij inne aplikacje i spróbuj ponownie.',
  model_checksum_mismatch: 'Pobrany plik jest uszkodzony. Ponów pobieranie.',
  model_size_mismatch: 'Pobrany plik jest niekompletny. Ponów pobieranie.',
};

export default function Onboarding() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [step, setStep] = useState(0);
  const [accessRequested, setAccessRequested] = useState(false);
  const [warningRequested, setWarningRequested] = useState(false);
  const attemptedDownload = useRef(false);
  const submitting = useRef(false);
  const { data: s, isError, refetch } = useGuardianStatus();
  const action = useAction();
  const replay = usePreferences(s => s.onboarded);
  const complete = usePreferences(s => s.completeOnboarding);
  const close = usePreferences(s => s.closeOnboarding);
  const native = process.env.EXPO_OS === 'android';
  const downloading = s?.modelDownloadState === 'downloading' || s?.modelDownloadState === 'installing';
  const installed = !!s?.modelInstalled && !downloading;
  const downloadError = s?.modelDownloadState === 'error' ? downloadErrors[s.modelDownloadError ?? ''] ?? 'Nie udało się przygotować ochrony. Sprawdź internet i spróbuj ponownie.' : null;

  // Starts once per visit. Native owns the job, so permission screens do not interrupt it.
  useEffect(() => {
    if (!native || !s?.available || s.modelInstalled || attemptedDownload.current) return;
    attemptedDownload.current = true;
    void action.run(guardian.downloadModel);
  }, [native, s?.available, s?.modelInstalled, action]);

  // Advance as soon as the refreshed Android status confirms the user's permission.
  if (step === 1 && accessRequested && s?.notificationAccess) { setAccessRequested(false); setStep(2); }
  if (step === 2 && warningRequested && s?.notificationPermission) { setWarningRequested(false); setStep(3); }

  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (step > 0) { setStep(value => value - 1); return true; }
      if (replay) { close(); return true; }
      return false;
    });
    return () => subscription.remove();
  }, [step, replay, close]);

  async function finish() {
    if (submitting.current) return;
    submitting.current = true;
    try {
      await action.run(async () => {
        if (native) {
          const latest = await guardian.status();
          if (!latest.modelInstalled || !latest.notificationAccess) throw new Error('setup_incomplete');
          if (!latest.notificationPermission) throw new Error('warning_permission_required');
          if (!latest.monitoringEnabled || latest.modelState !== 'ready') await guardian.monitor(true);
        }
        complete();
      });
    } finally { submitting.current = false; }
  }

  return <View style={{ flex: 1, backgroundColor: colors.background }}>
    <StepTransition step={step}>{(displayedStep, transitioning) => {
      const current = steps[displayedStep];
      const welcome = displayedStep === 0;
      const busy = action.busy || transitioning;
      return <View style={{ flex: 1, width: '100%', maxWidth: 520, alignSelf: 'center' }}>
      <Row style={{ paddingTop: insets.top + 12, paddingHorizontal: 24, minHeight: insets.top + 60, justifyContent: welcome ? 'flex-end' : 'space-between' }}>
        {!welcome && <Pressable accessibilityRole="button" accessibilityLabel="Wróć do poprzedniego kroku" disabled={busy} onPress={() => setStep(value => value - 1)} style={{ minHeight: 48, justifyContent: 'center' }}><Row style={{ gap: 8 }}><Icon name="back" size={24} /><Copy style={{ color: colors.text }}>Wróć</Copy></Row></Pressable>}
      </Row>
      <ScrollView key={displayedStep} contentInsetAdjustmentBehavior="automatic" contentContainerStyle={{ padding: 24, gap: 24, ...(welcome ? { flexGrow: 1, justifyContent: 'center', alignItems: 'center' } as const : {}) }}>
        {!welcome && <View style={{ gap: 24 }}>
          <View style={{ width: 48, height: 48 }}><Icon name={current.icon} size={48} /></View>
          <Copy title accessibilityRole="header" style={{ fontSize: 28, lineHeight: 36 }}>{displayedStep === 3 && native && !installed ? 'Chwila i gotowe' : current.title}</Copy>
        </View>}
        {welcome && <>
          <View accessibilityLabel="Guardian" accessible style={{ alignItems: 'center', gap: 16 }}>
            <Icon name="shield" size={112} />
            <Copy style={{ fontFamily: 'ManropeBold', fontSize: 44, lineHeight: 56, letterSpacing: -2, color: colors.text }}>guardian.</Copy>
          </View>
          <Copy title accessibilityRole="header" style={{ fontSize: 28, lineHeight: 36, textAlign: 'center' }}>Chroń swoje pieniądze</Copy>
          <Copy style={{ textAlign: 'center' }}>Ostrzeżenie przed oszustwem.
Prosta podpowiedź, co zrobić.</Copy>
          <Row style={{ gap: 8 }}><Icon name="lock" size={24} /><Copy style={{ flexShrink: 1, fontSize: 18, lineHeight: 28 }}>Wiadomości zostają na telefonie.</Copy></Row>
          {!native && <Copy style={{ fontSize: 16, lineHeight: 24, textAlign: 'center' }}>Wersja pokazowa · Android</Copy>}
        </>}
        {displayedStep === 1 && <>
          <Copy>Pozwól Guardian czytać nowe powiadomienia i wykrywać podejrzane wiadomości.</Copy>
          {s?.notificationAccess ? <Row><Icon name="check" size={28} /><Copy>Dostęp włączony.</Copy></Row> : <Card><Copy>Włącz dostęp dla Guardian. Potem wróć tutaj.</Copy></Card>}
          {accessRequested && !s?.notificationAccess && <Copy accessibilityLiveRegion="polite">Dostęp nadal jest wyłączony.</Copy>}
        </>}
        {displayedStep === 2 && <>
          <Copy>Dostaniesz powiadomienie, gdy wiadomość wygląda podejrzanie.</Copy>
          <Card><Row><Icon name="warning" size={28} /><Copy style={{ color: colors.text, fontFamily: 'DMSansSemiBold', flex: 1 }}>Podejrzana prośba o pieniądze</Copy></Row><Copy>Zadzwoń do tej osoby na znany numer, zanim wykonasz przelew.</Copy></Card>
          {s?.notificationPermission && <Row><Icon name="check" size={28} /><Copy>Ostrzeżenia włączone.</Copy></Row>}
          {native && warningRequested && !s?.notificationPermission && <Copy>Włącz powiadomienia w ustawieniach telefonu.</Copy>}
        </>}
        {displayedStep === 3 && <>
          <Copy>{native ? installed ? 'Ochrona będzie działać w tle. Korzystaj z telefonu jak zwykle.' : 'Przygotowujemy ochronę. To może potrwać kilka minut.' : 'Poznaj Guardian na przykładowych wiadomościach.'}</Copy>
          {native && !s?.notificationAccess && <Action label="Zezwól na dostęp" secondary onPress={() => setStep(1)} />}
          {native && !s?.notificationPermission && <Action label="Zezwól na ostrzeżenia" secondary onPress={() => setStep(2)} />}
        </>}
        {native && <View style={{ gap: 12 }}>
          {downloading && !welcome && <>
            <Row><ActivityIndicator color={colors.text} /><Copy accessibilityLiveRegion="polite" style={{ fontSize: 16, flex: 1 }}>{s?.modelDownloadState === 'installing' ? 'Jeszcze chwila…' : `Przygotowanie ochrony · ${s?.modelDownloadProgress ?? 0}%`}</Copy></Row>
            <View accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 100, now: s?.modelDownloadProgress ?? 0 }} style={{ height: 4, backgroundColor: colors.border, borderRadius: 2, overflow: 'hidden' }}><View style={{ height: 4, width: `${s?.modelDownloadProgress ?? 0}%`, backgroundColor: colors.text }} /></View>
          </>}
          <InlineError message={downloadError} />
          {downloadError && !s?.modelInstalled && <Action secondary label="Ponów pobieranie" disabled={busy} onPress={() => { void action.run(guardian.downloadModel); }} />}
          {s && !s.available && <InlineError message="Zainstaluj pełną wersję Guardian na Androidzie, aby włączyć ochronę." />}
          {isError && <><InlineError message="Nie można odczytać stanu ochrony." /><Action secondary label="Sprawdź ponownie" onPress={() => { void refetch(); }} /></>}
        </View>}
      </ScrollView>
      <View style={{ paddingHorizontal: 24, paddingTop: 12, paddingBottom: insets.bottom + 16, gap: 12, borderTopWidth: 1, borderTopColor: colors.border }}>
        <InlineError message={action.error} />
        <Action
          label={action.busy ? 'Chwila…' : displayedStep === 0 ? 'Zaczynamy' : displayedStep === 1 ? !native || s?.notificationAccess ? 'Dalej' : 'Zezwól na dostęp' : displayedStep === 2 ? !native || s?.notificationPermission ? 'Dalej' : warningRequested ? 'Otwórz ustawienia' : 'Zezwól na ostrzeżenia' : native ? installed ? 'Włącz ochronę' : 'Przygotowujemy ochronę…' : 'Wypróbuj Guardian'}
          disabled={busy || (native && displayedStep > 0 && (!s?.available || isError)) || (native && displayedStep === 3 && (!installed || !s?.notificationAccess || !s?.notificationPermission))}
          onPress={() => {
            if (displayedStep === 0) { setStep(1); return; }
            if (displayedStep === 1) {
              if (!native || s?.notificationAccess) { setStep(2); return; }
              setAccessRequested(true); void action.run(guardian.openSettings); return;
            }
            if (displayedStep === 2) {
              if (!native || s?.notificationPermission) { setStep(3); return; }
              if (warningRequested) { void action.run(Linking.openSettings); return; }
              setWarningRequested(true); void action.run(async () => guardian.warningPermission()); return;
            }
            void finish();
          }}
        />
        {welcome && native && <View style={{ alignItems: 'center', gap: 4 }}>
          {!installed && <Copy style={{ fontSize: 16, lineHeight: 24 }}>Jednorazowe pobranie: 584 MB</Copy>}
          <Pressable accessibilityRole="link" onPress={() => { void Linking.openURL(model.licenseUrl); }} style={{ minHeight: 44, justifyContent: 'center' }}><Copy style={{ textAlign: 'center', fontSize: 16, lineHeight: 24 }}>Warunki korzystania z Gemma</Copy></Pressable>
        </View>}
      </View>
    </View>;
    }}</StepTransition>
  </View>;
}
