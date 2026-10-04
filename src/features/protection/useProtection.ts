import { t, useLanguage } from '@/i18n/legacy';
import * as Linking from 'expo-linking';
import { useMemo } from 'react';
import { useDemo, type Threat } from '@/features/demo/store';
import { useGuardianStatus, useGuardianResults, useAction } from '@/services/queries';
import { guardian } from '@/services/guardian';
import { toThreat } from '@/features/protection/presentation';

export function useProtection() { useLanguage();
  const demo = useDemo();
  const status = useGuardianStatus();
  const results = useGuardianResults();
  const action = useAction();
  const native = process.env.EXPO_OS === 'android';
  const s = status.data;
  const threats = useMemo<Threat[]>(() => native ? (results.data ?? []).map(toThreat) : demo.threats, [native, results.data, demo.threats]);
  const stateLabel = !native ? t('Tryb demonstracyjny') : status.isPending ? t('Sprawdzam stan…')
    : status.isError ? t('Nie można odczytać stanu ochrony') : !s?.available ? t('Wymagany build Android')
    : s.modelDownloadState === 'downloading' ? `Przygotowuję ochronę · ${s.modelDownloadProgress}%` : s.modelDownloadState === 'installing' ? t('Przygotowuję ochronę…')
    : !s.modelInstalled ? t('Dokończ przygotowanie ochrony') : !s.notificationAccess ? t('Włącz dostęp do powiadomień')
    : s.modelState === 'loading' || action.busy ? t('Uruchamiam silnik…')
    : !s.monitoringEnabled ? t('Monitorowanie wstrzymane') : !s.listenerConnected ? t('Oczekiwanie na połączenie z Androidem')
    : s.modelState !== 'ready' ? t('Silnik nie jest gotowy') : s.processing ? t('Analizuję nowe powiadomienie…') : t('Gemma · analiza lokalna');
  return {
    loadingResults: native && results.isPending, resultsError: native && results.isError, refreshResults: results.refetch,
    native, status: s, threats, stateLabel, busy: action.busy, error: action.error ?? (status.isError ? t('Nie można odczytać stanu ochrony.') : results.isError ? t('Nie można odczytać historii.') : null),
    enabled: native ? (s?.monitoringEnabled ?? false) : demo.enabled,
    active: native ? (s?.active ?? false) : demo.enabled,
    notifications: native ? (s?.notificationPermission ?? false) : demo.notifications,
    apps: native ? { SMS: true, WhatsApp: true, Messenger: true, Beeper: true } : demo.apps,
    toggleProtection: () => {
      if (!native) { demo.toggleProtection(); return; }
      void action.run(async () => {
        const latest = await guardian.status();
        if (!latest.available) throw new Error('native_unavailable');
        if (!latest.monitoringEnabled && !latest.modelInstalled) { await guardian.downloadModel(); return; }
        if (!latest.monitoringEnabled && !latest.notificationAccess) { await guardian.openSettings(); return; }
        await guardian.monitor(!latest.monitoringEnabled);
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
