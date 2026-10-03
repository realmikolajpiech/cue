import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { GuardianResult } from '@/types/guardian';
export type Threat = { id: string; title: string; source: string; time: string; risk: string; signals: string[]; advice: string; explanation?: string; reviewed: boolean; notificationContext?: GuardianResult['notificationContext'] };
const threats: Threat[] = [
  { id: '1', title: 'Podszywanie się pod bliską osobę', source: 'WhatsApp', time: 'Dzisiaj, 10:42', risk: 'Wysokie ryzyko', signals: ['Nowy numer', 'Presja czasu', 'Prośba o pieniądze'], advice: 'Zadzwoń do tej osoby na wcześniej znany numer. Nie wysyłaj pieniędzy, zanim potwierdzisz jej tożsamość.', reviewed: false },
  { id: '2', title: 'Podejrzany link do płatności', source: 'SMS', time: 'Wczoraj, 16:18', risk: 'Wysokie ryzyko', signals: ['Nieznany link', 'Prośba o płatność'], advice: 'Otwórz oficjalną aplikację usługodawcy. Nie korzystaj z linku w wiadomości.', reviewed: true },
  { id: '3', title: 'Prośba o kod weryfikacyjny', source: 'Messenger', time: 'Wczoraj, 09:05', risk: 'Średnie ryzyko', signals: ['Prośba o kod', 'Niepotwierdzona tożsamość'], advice: 'Nie udostępniaj kodów weryfikacyjnych. Skontaktuj się z nadawcą innym kanałem.', reviewed: true },
];
const demoContexts = [
  { title: 'Nieznany numer', sender: '+48 000 000 001', text: 'Mamo, to mój nowy numer. Potrzebuję pilnie pieniędzy, możesz zrobić przelew?' },
  { title: 'Przesyłka', sender: 'Przesyłka', text: 'Twoja paczka czeka na dopłatę 2,99 zł. Zapłać tutaj: https://doplaty.example/odbior' },
  { title: 'Znajomy', sender: 'Znajomy', text: 'Podeślij mi kod, który właśnie dostałeś SMS-em. Potrzebuję go do potwierdzenia.' },
];
threats.forEach((threat, index) => {
  const { title, sender, text } = demoContexts[index];
  threat.notificationContext = { title, receivedAt: Date.now(), messages: [{ sender, text }] };
});
type Demo = { hydrated: boolean; dark: boolean; enabled: boolean; notifications: boolean; apps: Record<string, boolean>; threats: Threat[]; toggleTheme: () => void; toggleProtection: () => void; toggleNotifications: () => void; toggleApp: (name: string) => void; review: (id: string) => void; clear: () => void };
// Only the appearance preference is persisted; demo data stays session-only.
export const useDemo = create<Demo>()(persist(set => ({ hydrated: false, dark: false, enabled: true, notifications: true, apps: { SMS: true, WhatsApp: true, Messenger: true }, threats,
  toggleTheme: () => set(s => ({ dark: !s.dark })), toggleProtection: () => set(s => ({ enabled: !s.enabled })), toggleNotifications: () => set(s => ({ notifications: !s.notifications })), toggleApp: name => set(s => ({ apps: { ...s.apps, [name]: !s.apps[name] } })), review: id => set(s => ({ threats: s.threats.map(t => t.id === id ? { ...t, reviewed: true } : t) })), clear: () => set({ threats: [] }),
}), {
  name: 'cue-appearance-v1', storage: createJSONStorage(() => AsyncStorage),
  skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined',
  partialize: state => ({ dark: state.dark }),
  onRehydrateStorage: () => () => useDemo.setState({ hydrated: true }),
}));
