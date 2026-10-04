import { t } from '@/i18n/legacy';
import { NativeModule, requireOptionalNativeModule } from 'expo';
import { resultSchema, manualResultSchema, statusSchema, notificationSchema, type GuardianStatus } from '@/types/guardian';

export type GuardianChange = { resultsChanged?: boolean };

declare class GuardianModule extends NativeModule<{ onChanged: (change: GuardianChange) => void }> {
  checkMessage(message: string): Promise<string>;
  runBenchmark(): Promise<string>;
  cancelBenchmark(): void;
  getStatus(): Promise<unknown>;
  getResults(): Promise<string[]>;
  getNotifications(): Promise<unknown[]>;
  setMonitoring(enabled: boolean): Promise<unknown>;
  clearHistory(): Promise<void>;
  markReviewed(id: string): Promise<void>;
  importModel(uri: string): Promise<string>;
  downloadModel(): Promise<unknown>;
  openWarningChannelSettings(): Promise<void>;
  openNotificationSettings(): Promise<void>;
  requestWarningPermission(): void;
}
const native = process.env.EXPO_OS === 'android' ? requireOptionalNativeModule<GuardianModule>('Guardian') : null;
const unavailable: GuardianStatus = { benchmarkRunning: false, benchmarkProgress: 0, available: false, notificationAccess: false, listenerConnected: false, monitoringEnabled: false, modelState: 'missing', modelDownloadState: 'idle', modelDownloadProgress: 0, modelDownloadError: null, backend: 'none', processing: false, error: null, active: false, modelInstalled: false, modelSha256: null, initializationMs: 0, promptVersion: 'guardian-pl-v3-evidence', runtimeVersion: '0.15.0', notificationPermission: false };
function requireGuardian() {
  if (!native) throw new Error(t("Funkcja wymaga własnego buildu Guardian na Androidzie."));
  return native;
}
export const guardian = {
  async notifications() {
    if (!native) return [];
    if (typeof native.getNotifications !== 'function') throw new Error('notification_history_build_required');
    return (await native.getNotifications()).map(value => notificationSchema.parse(value));
  },
  async checkMessage(message: string) { return manualResultSchema.parse(JSON.parse(await requireGuardian().checkMessage(message))); },
  async status() { return native ? statusSchema.parse(await native.getStatus()) : unavailable; },
  async results() { return native ? (await native.getResults()).map(value => resultSchema.parse(JSON.parse(value))) : []; },
  async monitor(enabled: boolean) { return statusSchema.parse(await requireGuardian().setMonitoring(enabled)); },
  benchmark: async () => JSON.parse(await requireGuardian().runBenchmark()) as { metrics: Record<string, number | null> },
  cancelBenchmark: () => requireGuardian().cancelBenchmark(),
  clear: () => requireGuardian().clearHistory(),
  review: (id: string) => requireGuardian().markReviewed(id),
  importModel: (uri: string) => requireGuardian().importModel(uri),
  downloadModel: async () => {
    const module = requireGuardian();
    if (typeof module.downloadModel !== 'function') throw new Error('native_update_required');
    return statusSchema.parse(await module.downloadModel());
  },
  openWarningChannelSettings: () => requireGuardian().openWarningChannelSettings(),
  openSettings: () => requireGuardian().openNotificationSettings(),
  warningPermission: () => requireGuardian().requestWarningPermission(),
  subscribe: (changed: (change: GuardianChange) => void) => native?.addListener('onChanged', changed),
};
