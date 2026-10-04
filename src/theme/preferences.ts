import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createJSONStorage, persist } from 'zustand/middleware';

type Appearance = { hydrated: boolean; dark: boolean; toggleTheme: () => void };

export const useAppearance = create<Appearance>()(persist(
  set => ({ hydrated: false, dark: false, toggleTheme: () => set(state => ({ dark: !state.dark })) }),
  {
    // Retain Cue's existing key so a cleanup does not reset the selected theme.
    name: 'cue-appearance-v1', storage: createJSONStorage(() => AsyncStorage),
    skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined',
    partialize: state => ({ dark: state.dark }),
    onRehydrateStorage: () => () => useAppearance.setState({ hydrated: true }),
  },
));
