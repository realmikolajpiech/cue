import * as Linking from 'expo-linking';
import { router } from 'expo-router';
import { useDemo, type Threat } from '@/features/demo/store';
import { useGuardianStatus, useGuardianResults, useAction } from '@/services/queries';
import { guardian } from '@/services/guardian';
import { CURRENT_ANALYSIS_VERSION, categoryLabels, riskLabels, signalLabels } from '@/types/guardian';

export function useProtection() {
  const demo = useDemo();
  const status = useGuardianStatus();
  const results = useGuardianResults();
  const action = useAction();
  const native = process.env.EXPO_OS === 'android';
  const s = status.data;
  const threats: Threat[] = native ? (results.data ?? []).map(result => ({
    id: result.id, title: result.analysisVersion === CURRENT_ANALYSIS_VERSION ? categoryLabels[result.category] : 'Starszy wynik — sprawdź wiadomość ponownie', source: result.sourceApp,
    time: new Date(result.createdAt).toLocaleString('pl-PL'), risk: riskLabels[result.analysisVersion === CURRENT_ANALYSIS_VERSION ? result.risk : 'uncertain'],
    signals: result.analysisVersion === CURRENT_ANALYSIS_VERSION ? result.signals.map(signal => signalLabels[signal]) : [], advice: result.recommendedAction,
    explanation: result.analysisVersion === CURRENT_ANALYSIS_VERSION ? result.explanation : 'Wcześniejsza wersja modelu zwracała niepotwierdzone sygnały. Ten wynik wymaga ponownego sprawdzenia.', reviewed: result.reviewStatus === 'reviewed',
  })) : demo.threats;
  const stateLabel = !native ? 'Tryb demonstracyjny' : status.isPending ? 'Sprawdzam stan…'
    : status.isError ? 'Nie można odczytać stanu ochrony' : !s?.available ? 'Wymagany build Android'
    : !s.modelInstalled ? 'Najpierw zainstaluj model Gemma' : !s.notificationAccess ? 'Włącz dostęp do powiadomień'
    : s.modelState === 'loading' || action.busy ? 'Uruchamiam silnik…'
    : !s.monitoringEnabled ? 'Monitorowanie wstrzymane' : !s.listenerConnected ? 'Oczekiwanie na połączenie z Androidem'
    : s.modelState !== 'ready' ? 'Silnik nie jest gotowy' : s.processing ? 'Analizuję nowe powiadomienie…' : 'Gemma · analiza lokalna';
  return {
    native, status: s, threats, stateLabel, busy: action.busy, error: action.error ?? (status.isError ? 'Nie można odczytać stanu ochrony.' : results.isError ? 'Nie można odczytać historii.' : null),
    enabled: native ? (s?.monitoringEnabled ?? false) : demo.enabled,
    active: native ? (s?.active ?? false) : demo.enabled,
    notifications: native ? (s?.notificationPermission ?? false) : demo.notifications,
    apps: native ? { SMS: true, WhatsApp: true, Messenger: true, Beeper: true } : demo.apps,
    toggleProtection: () => {
      if (!native) { demo.toggleProtection(); return; }
      void action.run(async () => {
        if (!s?.available) throw new Error('native_unavailable');
        if (!s.monitoringEnabled && !s.modelInstalled) { router.push('/model-setup'); return; }
        if (!s.monitoringEnabled && !s.notificationAccess) { await guardian.openSettings(); return; }
        await guardian.monitor(!s.monitoringEnabled);
      });
    },
    toggleNotifications: () => {
      if (!native) { demo.toggleNotifications(); return; }
      if (s?.notificationPermission) { void action.run(Linking.openSettings); }
      else guardian.warningPermission();
    },
    toggleApp: demo.toggleApp,
    review: (id: string) => native ? action.run(() => guardian.review(id)) : Promise.resolve(demo.review(id)),
    clear: () => native ? action.run(guardian.clear) : Promise.resolve(demo.clear()),
    openAccess: () => { void action.run(guardian.openSettings); },
  };
}
