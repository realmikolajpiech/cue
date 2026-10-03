import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export const useSubtextPreferences = create<{ onboarded: boolean; hydrated: boolean; finish: () => void }>()(persist(
  set => ({ onboarded: false, hydrated: false, finish: () => set({ onboarded: true }) }),
  { name: 'subtext-preferences-v1', storage: createJSONStorage(() => AsyncStorage), partialize: state => ({ onboarded: state.onboarded }),
    onRehydrateStorage: () => () => useSubtextPreferences.setState({ hydrated: true }) },
));
