import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { changeLanguage, type Language } from '@/i18n';

type Preferences = {
  onboarded: boolean;
  hydrated: boolean;
  language: Language;
  completeOnboarding: () => void;
  setLanguage: (language: Language) => void;
  setHydrated: () => void;
};
export const usePreferences = create<Preferences>()(persist(
  set => ({
    onboarded: false, hydrated: false, language: 'pl',
    completeOnboarding: () => set({ onboarded: true }),
    setLanguage: language => { changeLanguage(language); set({ language }); },
    setHydrated: () => set({ hydrated: true }),
  }),
  {
    skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined',
    name: 'guardian-preferences-v1', storage: createJSONStorage(() => AsyncStorage),
    partialize: state => ({ onboarded: state.onboarded, language: state.language }),
    onRehydrateStorage: () => state => {
      const language = state?.language === 'en' ? 'en' : 'pl';
      changeLanguage(language);
      usePreferences.setState({ language, hydrated: true });
    },
  },
));
