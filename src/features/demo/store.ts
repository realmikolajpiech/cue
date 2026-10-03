import { create } from 'zustand';
export type Threat = { id: string; title: string; source: string; time: string; risk: string; signals: string[]; advice: string; explanation?: string; reviewed: boolean };
const threats: Threat[] = [
  { id: '1', title: 'Podszywanie się pod bliską osobę', source: 'WhatsApp', time: 'Dzisiaj, 10:42', risk: 'Wysokie ryzyko', signals: ['Nowy numer', 'Presja czasu', 'Prośba o pieniądze'], advice: 'Zadzwoń do tej osoby na wcześniej znany numer. Nie wysyłaj pieniędzy, zanim potwierdzisz jej tożsamość.', reviewed: false },
  { id: '2', title: 'Podejrzany link do płatności', source: 'SMS', time: 'Wczoraj, 16:18', risk: 'Wysokie ryzyko', signals: ['Nieznany link', 'Prośba o płatność'], advice: 'Otwórz oficjalną aplikację usługodawcy. Nie korzystaj z linku w wiadomości.', reviewed: true },
  { id: '3', title: 'Prośba o kod weryfikacyjny', source: 'Messenger', time: 'Wczoraj, 09:05', risk: 'Średnie ryzyko', signals: ['Prośba o kod', 'Niepotwierdzona tożsamość'], advice: 'Nie udostępniaj kodów weryfikacyjnych. Skontaktuj się z nadawcą innym kanałem.', reviewed: true },
];
type Demo = { dark: boolean; enabled: boolean; notifications: boolean; apps: Record<string, boolean>; threats: Threat[]; toggleTheme: () => void; toggleProtection: () => void; toggleNotifications: () => void; toggleApp: (name: string) => void; review: (id: string) => void; clear: () => void };
// Session-only presentation state. No native engine, permissions or persisted messages.
export const useDemo = create<Demo>(set => ({ dark: false, enabled: true, notifications: true, apps: { SMS: true, WhatsApp: true, Messenger: true }, threats,
  toggleTheme: () => set(s => ({ dark: !s.dark })), toggleProtection: () => set(s => ({ enabled: !s.enabled })), toggleNotifications: () => set(s => ({ notifications: !s.notifications })), toggleApp: name => set(s => ({ apps: { ...s.apps, [name]: !s.apps[name] } })), review: id => set(s => ({ threats: s.threats.map(t => t.id === id ? { ...t, reviewed: true } : t) })), clear: () => set({ threats: [] }),
}));
