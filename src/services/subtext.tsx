import { NativeModule, requireOptionalNativeModule } from 'expo';
import { QueryClient, QueryClientProvider, focusManager, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { z } from 'zod';
import { profileSchema, roomSchema, subtextStatusSchema, type Network } from '@/types/subtext';

declare class SubtextModule extends NativeModule<{ onChanged: () => void }> {
  status(): Promise<string>;
  loadDemo(): Promise<string>;
  conversations(): Promise<string>;
  conversation(id: string): Promise<string>;
  refresh(): Promise<void>;
  analyze(id: string, draft: string): Promise<string>;
  setCloudEnabled(enabled: boolean): Promise<void>;
  clearHistory(): Promise<void>;
  disconnect(network: Network): Promise<void>;
  connectMessenger(): Promise<void>;
  pairWhatsApp(phone: string): Promise<string>;
  openKeyboardSettings(): Promise<void>;
  selectKeyboard(): void;
}
const native = process.env.EXPO_OS === 'android' ? requireOptionalNativeModule<SubtextModule>('Subtext') : null;
function requireSubtext() { if (!native) throw new Error('Zainstaluj build Cue na Androidzie. Expo Go i podgląd web nie obsługują komunikatorów.'); return native; }
const unavailable = {
  available: false, hasApiKey: false, cloudEnabled: false, backgroundEnabled: false, model: 'deepseek-flash', analyzing: null,
  messenger: { phase: 'NOT_CONFIGURED', detail: '' }, whatsapp: { phase: 'NOT_CONFIGURED', detail: '', pairingCode: null },
};
export const subtext = {
  demo: () => requireSubtext().loadDemo(),
  status: async () => native ? subtextStatusSchema.parse(JSON.parse(await native.status())) : unavailable,
  rooms: async () => native ? z.array(roomSchema).parse(JSON.parse(await native.conversations())) : [],
  room: async (id: string) => roomSchema.parse(JSON.parse(await requireSubtext().conversation(id))),
  refresh: () => requireSubtext().refresh(),
  analyze: async (id: string, draft = '') => profileSchema.parse(JSON.parse(await requireSubtext().analyze(id, draft))),
  cloud: (enabled: boolean) => requireSubtext().setCloudEnabled(enabled),
  clear: () => requireSubtext().clearHistory(),
  disconnect: (network: Network) => requireSubtext().disconnect(network),
  messenger: () => requireSubtext().connectMessenger(),
  whatsapp: (phone: string) => requireSubtext().pairWhatsApp(phone),
  keyboardSettings: () => requireSubtext().openKeyboardSettings(),
  keyboard: () => requireSubtext().selectKeyboard(),
};
export const subtextCache = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 5000, networkMode: 'always' } } });
export function SubtextProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const invalidate = () => {
      if (timer) return;
      timer = setTimeout(() => {
        timer = undefined;
        void subtextCache.invalidateQueries({ queryKey: ['subtext', 'status'] });
        void subtextCache.invalidateQueries({ queryKey: ['subtext', 'rooms'] });
        void subtextCache.invalidateQueries({ queryKey: ['subtext', 'room'] });
      }, 500);
    };
    const subscription = native?.addListener('onChanged', invalidate);
    const state = AppState.addEventListener('change', state => { focusManager.setFocused(state === 'active'); if (state === 'active') invalidate(); });
    return () => { subscription?.remove(); state.remove(); if (timer) clearTimeout(timer); };
  }, []);
  return <QueryClientProvider client={subtextCache}>{children}</QueryClientProvider>;
}
export function useSubtextStatus() { return useQuery({ queryKey: ['subtext', 'status'], queryFn: subtext.status, refetchInterval: 10000 }); }
export function useRooms() { return useQuery({ queryKey: ['subtext', 'rooms'], queryFn: subtext.rooms }); }
export function useRoom(id: string) { return useQuery({ queryKey: ['subtext', 'room', id], queryFn: () => subtext.room(id), enabled: !!id }); }
export function useSubtextAction() {
  const client = useQueryClient();
  return useMutation({ mutationFn: (action: () => Promise<unknown>) => action(), onSuccess: () => client.invalidateQueries({ queryKey: ['subtext'] }) });
}
