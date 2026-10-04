import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

type Preferences = {
  developerMode: boolean; toggleDeveloperMode: () => void; demoMode: boolean; toggleDemoMode: () => void;
  /** Display-only names shown in demo mode, keyed by conversation id. The stored conversation keeps its real name. */
  demoNames: Record<string, string>; setDemoName: (id: string, name: string) => void;
};
type Stored = Pick<Preferences, 'developerMode' | 'demoMode' | 'demoNames'>;
const values = (state: unknown): Stored => {
  const saved = state as Partial<Preferences> | null;
  const names = saved?.demoNames && typeof saved.demoNames === 'object' ? saved.demoNames : {};
  return {
    developerMode: saved?.developerMode === true, demoMode: saved?.demoMode === true,
    demoNames: Object.fromEntries(Object.entries(names).filter(([, name]) => typeof name === 'string' && name.trim())),
  };
};

const storage = createJSONStorage<Stored>(() => ({
  // Read the previous key only as an upgrade fallback. New writes use Cue's key.
  getItem: async (name: string) => await AsyncStorage.getItem(name) ?? await AsyncStorage.getItem('guardian-preferences-v1'),
  setItem: (name: string, value: string) => AsyncStorage.setItem(name, value),
  removeItem: (name: string) => AsyncStorage.removeItem(name),
}));
export const usePreferences = create<Preferences>()(persist<Preferences, [], [], Stored>(
  set => ({
    developerMode: false, toggleDeveloperMode: () => set(state => ({ developerMode: !state.developerMode })),
    demoMode: false, toggleDemoMode: () => set(state => ({ demoMode: !state.demoMode })),
    demoNames: {}, setDemoName: (id, name) => set(state => {
      const { [id]: _, ...demoNames } = state.demoNames;
      return { demoNames: name.trim() ? { ...demoNames, [id]: name.trim() } : demoNames };
    }),
  }),
  {
    skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined', name: 'cue-preferences-v1', version: 4,
    storage, partialize: values, migrate: values,
    merge: (persisted, current) => ({ ...current, ...values(persisted) }),
  },
));
