import { NativeModule, requireOptionalNativeModule } from 'expo';
import { QueryClient, QueryClientProvider, focusManager, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { z } from 'zod';
import { profileSchema, roomSchema, subtextStatusSchema, writingStyleSchema, reminderSchema, type Network, type WritingTone } from '@/types/subtext';
import { withDeadline } from './deadline';

declare class SubtextModule extends NativeModule<{ onChanged: () => void }> {
  status(): Promise<string>;
  loadDemo(): Promise<string>;
  conversationWritingStyle(id: string): Promise<string>;
  conversationReminders(id: string): Promise<string>;
  refreshConversationReminders(id: string): Promise<string>;
  editConversationReminder(id: string, reminderId: string, patch: string): Promise<void>;
  conversationMemory(id: string): Promise<string>;
  previewConversationWritingStyle(id: string): Promise<string>;
  setWritingTone(id: string | null, tone: WritingTone): Promise<string>;
  writingStyle(): Promise<string>;
  previewWritingStyle(): Promise<string>;
  conversations(): Promise<string>;
  conversation(id: string): Promise<string>;
  conversationImage(id: string, messageId: string): Promise<string>;
  syncConversation(id: string): Promise<string>;
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
  conversationReminders: async (id: string) => z.array(reminderSchema).parse(JSON.parse(await requireSubtext().conversationReminders(id))),
  refreshConversationReminders: async (id: string) => z.array(reminderSchema).parse(JSON.parse(await requireSubtext().refreshConversationReminders(id))),
  editConversationReminder: (id: string, reminderId: string, patch: { text?: string; dueDate?: string; status?: 'open' | 'tentative' | 'done' | 'cancelled'; delete?: boolean }) =>
    requireSubtext().editConversationReminder(id, reminderId, JSON.stringify(patch)),
  conversationMemory: async (id: string) => z.object({ conversationId: z.string(), storedMemory: z.record(z.string(), z.unknown()), aiMemory: z.record(z.string(), z.unknown()) })
    .parse(JSON.parse(await requireSubtext().conversationMemory(id))),
  setWritingTone: async (id: string | undefined, tone: WritingTone) => writingStyleSchema.parse(JSON.parse(await requireSubtext().setWritingTone(id ?? null, tone))),
  conversationWritingStyle: async (id: string) => writingStyleSchema.parse(JSON.parse(await requireSubtext().conversationWritingStyle(id))),
  previewConversationWritingStyle: async (id: string) => writingStyleSchema.parse(JSON.parse(await requireSubtext().previewConversationWritingStyle(id))),
  previewWritingStyle: async () => writingStyleSchema.parse(JSON.parse(await requireSubtext().previewWritingStyle())),
  writingStyle: async () => native ? writingStyleSchema.parse(JSON.parse(await native.writingStyle())) :
    writingStyleSchema.parse({ sampleCount: 0, conversationCount: 0, summary: '', habits: [], examples: [] }),
  demo: () => requireSubtext().loadDemo(),
  status: async () => native ? subtextStatusSchema.parse(JSON.parse(await native.status())) : unavailable,
  rooms: async () => native ? z.array(roomSchema).parse(JSON.parse(await native.conversations())) : [],
  room: async (id: string) => roomSchema.parse(JSON.parse(await withDeadline(requireSubtext().conversation(id), 5000, 'Nie udało się odczytać zapisanej rozmowy. Spróbuj ponownie.'))),
  image: (id: string, messageId: string) => withDeadline(requireSubtext().conversationImage(id, messageId), 50000, 'Pobieranie zdjęcia trwa zbyt długo. Spróbuj ponownie.'),
  syncRoom: async (id: string) => roomSchema.parse(JSON.parse(await withDeadline(requireSubtext().syncConversation(id), 25000, 'Synchronizacja trwa zbyt długo. Zapisane wiadomości są nadal dostępne. Spróbuj ponownie.'))),
  refresh: () => withDeadline(requireSubtext().refresh(), 30000, 'Nie udało się odświeżyć rozmów. Sprawdź połączenie komunikatora i spróbuj ponownie.'),
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
        void subtextCache.invalidateQueries({ queryKey: ['subtext', 'writing-style'] });
        void subtextCache.invalidateQueries({ queryKey: ['subtext', 'memory'] });
        void subtextCache.invalidateQueries({ queryKey: ['subtext', 'reminders'] });
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
export function useRoom(id: string) { return useQuery({ queryKey: ['subtext', 'room', id], queryFn: () => subtext.room(id), enabled: !!id, staleTime: 0, retry: false }); }
export function useSyncRoom(id: string, enabled: boolean, updatedAt = 0) {
  return useQuery({ // A new inbox timestamp requests recent messages while the saved room stays visible.
    queryKey: ['subtext', 'sync', id, updatedAt], enabled: !!id && enabled, retry: false, staleTime: 60000,
    refetchInterval: 60000, refetchOnWindowFocus: true, refetchOnReconnect: true,
    queryFn: async () => {
      const room = await subtext.syncRoom(id);
      subtextCache.setQueryData(['subtext', 'room', id], room);
      void subtextCache.invalidateQueries({ queryKey: ['subtext', 'rooms'] });
      return room;
    },
  });
}
export function useSubtextAction() {
  const client = useQueryClient();
  return useMutation({ mutationFn: (action: () => Promise<unknown>) => action(), onSuccess: () => client.invalidateQueries({ queryKey: ['subtext'] }) });
}
