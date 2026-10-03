import { focusManager, type QueryClient } from '@tanstack/react-query';
import type { GuardianChange } from './guardian';

type Subscription = { remove(): void };
type AppLifecycle = {
  currentState: string | null;
  addEventListener(event: 'change', listener: (state: string) => void): Subscription;
};
type Subscribe = (listener: (change?: GuardianChange) => void) => Subscription | undefined;

export function attachGuardianSync(client: QueryClient, subscribe: Subscribe, appState: AppLifecycle, web: boolean) {
  if (!web && appState.currentState !== null) {
    focusManager.setFocused(appState.currentState === 'active');
  }
  const native = subscribe(change => {
    const refetchType = focusManager.isFocused() ? 'active' : 'none';
    void client.invalidateQueries({ queryKey: ['guardian', 'status'], refetchType });
    // Older installed native builds send an empty event; retain their full-refresh behavior.
    if (change?.resultsChanged !== false) {
      void client.invalidateQueries({ queryKey: ['guardian', 'results'], refetchType });
    }
  });
  const app = appState.addEventListener('change', state => {
    if (web) return;
    if (state === 'active') {
      void client.invalidateQueries({ queryKey: ['guardian'], refetchType: 'none' });
    }
    focusManager.setFocused(state === 'active');
  });
  return () => { native?.remove(); app.remove(); };
}
