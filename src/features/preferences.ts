import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

type Preferences = { onboarded: boolean; hydrated: boolean; completeOnboarding: () => void; setHydrated: () => void };
export const usePreferences = create<Preferences>()(persist(
  set => ({ onboarded: false, hydrated: false, completeOnboarding: () => set({ onboarded: true }), setHydrated: () => set({ hydrated: true }) }),
  { skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined', name: 'guardian-preferences-v1', storage: createJSONStorage(() => AsyncStorage), partialize: state => ({ onboarded: state.onboarded }), onRehydrateStorage: () => () => usePreferences.getState().setHydrated() },
));
