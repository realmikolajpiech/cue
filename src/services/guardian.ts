import { NativeModule, requireOptionalNativeModule } from 'expo';
import { resultSchema, manualResultSchema, statusSchema, type GuardianStatus } from '@/types/guardian';

declare class GuardianModule extends NativeModule<{ onChanged: () => void }> {
  checkMessage(message: string): Promise<string>;
  runBenchmark(): Promise<string>;
  cancelBenchmark(): void;
  getStatus(): Promise<unknown>;
  getResults(): Promise<string[]>;
  setMonitoring(enabled: boolean): Promise<unknown>;
  clearHistory(): Promise<void>;
  markReviewed(id: string): Promise<void>;
  importModel(uri: string): Promise<string>;
  openNotificationSettings(): Promise<void>;
  requestWarningPermission(): void;
}
const native = process.env.EXPO_OS === 'android' ? requireOptionalNativeModule<GuardianModule>('Guardian') : null;
const unavailable: GuardianStatus = { benchmarkRunning: false, benchmarkProgress: 0, available: false, notificationAccess: false, listenerConnected: false, monitoringEnabled: false, modelState: 'missing', backend: 'none', processing: false, error: null, active: false, modelInstalled: false, modelSha256: null, initializationMs: 0, promptVersion: 'guardian-pl-v2', runtimeVersion: '0.15.0', notificationPermission: false };
function requireGuardian() {
  if (!native) throw new Error('Funkcja wymaga własnego buildu Guardian na Androidzie.');
  return native;
}
export const guardian = {
  async checkMessage(message: string) { return manualResultSchema.parse(JSON.parse(await requireGuardian().checkMessage(message))); },
  async status() { return native ? statusSchema.parse(await native.getStatus()) : unavailable; },
  async results() { return native ? (await native.getResults()).map(value => resultSchema.parse(JSON.parse(value))) : []; },
  async monitor(enabled: boolean) { return statusSchema.parse(await requireGuardian().setMonitoring(enabled)); },
  benchmark: async () => JSON.parse(await requireGuardian().runBenchmark()) as { metrics: Record<string, number | null> },
  cancelBenchmark: () => requireGuardian().cancelBenchmark(),
  clear: () => requireGuardian().clearHistory(),
  review: (id: string) => requireGuardian().markReviewed(id),
  importModel: (uri: string) => requireGuardian().importModel(uri),
  openSettings: () => requireGuardian().openNotificationSettings(),
  warningPermission: () => requireGuardian().requestWarningPermission(),
  subscribe: (changed: () => void) => native?.addListener('onChanged', changed),
};
