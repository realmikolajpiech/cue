import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { guardian } from './guardian';

const client = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 5000 } } });
function Sync() {
  useEffect(() => {
    const refresh = () => { void client.invalidateQueries({ queryKey: ['guardian'] }); };
    const native = guardian.subscribe(refresh);
    const app = AppState.addEventListener('change', state => { if (state === 'active') refresh(); });
    return () => { native?.remove(); app.remove(); };
  }, []);
  return null;
}
export function Providers({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}><Sync />{children}</QueryClientProvider>;
}
export function useGuardianStatus() { return useQuery({ queryKey: ['guardian', 'status'], queryFn: guardian.status, refetchInterval: 5000 }); }
export function useGuardianResults() { return useQuery({ queryKey: ['guardian', 'results'], queryFn: guardian.results }); }
export function useAction() {
  const cache = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function run(action: () => Promise<unknown>) {
    if (busy) return;
    setBusy(true); setError(null);
    try { await action(); await cache.invalidateQueries({ queryKey: ['guardian'] }); }
    catch { setError('Nie udało się wykonać działania. Sprawdź stan modelu i spróbuj ponownie.'); }
    finally { setBusy(false); }
  }
  return { busy, error, run };
}
