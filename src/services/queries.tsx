import { t, useLanguage } from '@/i18n/legacy';
import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { guardian } from './guardian';
import { attachGuardianSync } from './guardianSync';

const client = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 5000, networkMode: 'always' } } });
const statusOptions = { queryKey: ['guardian', 'status'] as const, queryFn: guardian.status };
function Sync() {
  // One polling observer shared by every screen, suspended when the app is in the background.
  useQuery({ ...statusOptions, refetchInterval: process.env.EXPO_OS === 'android' ? 5000 : false });
  useEffect(() => attachGuardianSync(client, guardian.subscribe, AppState, process.env.EXPO_OS === 'web'), []);
  return null;
}
export function Providers({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}><Sync />{children}</QueryClientProvider>;
}
export function useGuardianStatus() { return useQuery(statusOptions); }
export function useGuardianResults() { return useQuery({ queryKey: ['guardian', 'results'], queryFn: guardian.results }); }
export function useAction() { useLanguage();
  const cache = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function run(action: () => Promise<unknown>) {
    if (busy) return;
    setBusy(true); setError(null);
    try { await action(); await cache.invalidateQueries({ queryKey: ['guardian'] }); }
    catch (cause) {
      const message = cause instanceof Error ? cause.message : '';
      const errors: Record<string, string> = {
        setup_incomplete: 'Dokończ pobieranie modelu i włącz dostęp do powiadomień.',
        warning_permission_required: 'Zezwól na ostrzeżenia, aby dokończyć przygotowanie ochrony.',
        model_download_failed: 'Nie udało się pobrać modelu. Sprawdź połączenie z internetem i spróbuj ponownie.',
        native_unavailable: 'Ochrona wymaga aplikacji Guardian na Androidzie.',
        native_update_required: 'Zainstaluj nowy build Guardian, aby automatycznie pobrać model.',
        model_size_mismatch: 'Nieprawidłowy rozmiar pliku. Pobierz pełny model Gemma 3 1B INT4 (584 MB).',
        model_checksum_mismatch: 'Plik jest uszkodzony lub jest inną wersją modelu. Pobierz wskazany plik Gemma ponownie.',
        model_insufficient_space: 'Brakuje miejsca na model. Zwolnij co najmniej 650 MB i spróbuj ponownie.',
        model_initialization_failed: 'Model nie uruchomił się na tym urządzeniu. Zamknij inne aplikacje i spróbuj ponownie.',
        invalid_format: 'Wybierz plik .litertlm wskazanego modelu Gemma, a nie plik .task.',
      };
      setError(Object.entries(errors).find(([code]) => message.includes(code))?.[1] ?? "Nie udało się wykonać działania. Sprawdź stan modelu i spróbuj ponownie.");
    }
    finally { setBusy(false); }
  }
  return { busy, error: error ? t(error) : null, run };
}
