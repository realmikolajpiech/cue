import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

type Preferences = { developerMode: boolean; toggleDeveloperMode: () => void };
const values = (state: unknown) => ({ developerMode: (state as Partial<Preferences> | null)?.developerMode === true });

const storage = createJSONStorage<Pick<Preferences, 'developerMode'>>(() => ({
  // Read the previous key only as an upgrade fallback. New writes use Cue's key.
  getItem: async (name: string) => await AsyncStorage.getItem(name) ?? await AsyncStorage.getItem('guardian-preferences-v1'),
  setItem: (name: string, value: string) => AsyncStorage.setItem(name, value),
  removeItem: (name: string) => AsyncStorage.removeItem(name),
}));
export const usePreferences = create<Preferences>()(persist<Preferences, [], [], Pick<Preferences, 'developerMode'>>(
  set => ({ developerMode: false, toggleDeveloperMode: () => set(state => ({ developerMode: !state.developerMode })) }),
  {
    skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined', name: 'cue-preferences-v1', version: 2,
    storage, partialize: values, migrate: values,
    merge: (persisted, current) => ({ ...current, ...values(persisted) }),
  },
));
