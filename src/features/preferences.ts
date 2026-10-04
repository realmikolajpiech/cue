import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { changeLanguage, deviceLanguage, type Language } from '@/i18n';

type Preferences = { developerMode: boolean; toggleDeveloperMode: () => void; onboarded: boolean; hydrated: boolean; onboardingOpen: boolean; language: Language; completeOnboarding: () => void; restartOnboarding: () => void; closeOnboarding: () => void; setLanguage: (language: Language) => void; setHydrated: () => void };
const toLanguage = (value: unknown): Language => value === 'en' || value === 'pl' ? value : deviceLanguage();
export const usePreferences = create<Preferences>()(persist(
  set => ({ developerMode: false, toggleDeveloperMode: () => set(state => ({ developerMode: !state.developerMode })), onboarded: false, hydrated: false, onboardingOpen: false, language: deviceLanguage(), completeOnboarding: () => set({ onboarded: true, onboardingOpen: false }), restartOnboarding: () => set({ onboardingOpen: true }), closeOnboarding: () => set({ onboardingOpen: false }), setLanguage: language => { changeLanguage(language); set({ language }); }, setHydrated: () => set({ hydrated: true }) }),
  {
    skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined', name: 'guardian-preferences-v1', version: 2,
    migrate: state => ({ onboarded: false, language: toLanguage((state as Partial<Preferences>)?.language) }),
    storage: createJSONStorage(() => AsyncStorage), partialize: state => ({ developerMode: state.developerMode, onboarded: state.onboarded, language: state.language }),
    onRehydrateStorage: () => state => {
      const language = toLanguage(state?.language);
      changeLanguage(language);
      usePreferences.setState({ language, hydrated: true });
    },
  },
));
