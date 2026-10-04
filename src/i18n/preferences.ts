import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { syncNativeLanguage } from '@/services/subtext';
import { changeLanguage as changeI18nLanguage, deviceLanguage, type Language } from './index';

// The Cue keyboard and AI prompts read the language from the native module.
function changeLanguage(language: Language) { changeI18nLanguage(language); syncNativeLanguage(language); }

type LanguagePreferences = {
  language: Language;
  hydrated: boolean;
  setLanguage: (language: Language) => void;
};
const toLanguage = (value: unknown): Language => value === 'en' || value === 'pl' ? value : deviceLanguage();
const values = (state: unknown) => ({ language: toLanguage((state as Partial<LanguagePreferences> | null)?.language) });
const storage = createJSONStorage<Pick<LanguagePreferences, 'language'>>(() => ({
  getItem: async name => await AsyncStorage.getItem(name) ?? await AsyncStorage.getItem('guardian-preferences-v1'),
  setItem: (name, value) => AsyncStorage.setItem(name, value),
  removeItem: name => AsyncStorage.removeItem(name),
}));

// Keep language persistence separate from the retired Guardian onboarding store.
export const useLanguagePreferences = create<LanguagePreferences>()(persist<LanguagePreferences, [], [], Pick<LanguagePreferences, 'language'>>(
  set => ({ language: deviceLanguage(), hydrated: false, setLanguage: language => { changeLanguage(language); set({ language }); } }),
  {
    name: 'cue-language-v1', version: 1, storage,
    skipHydration: process.env.EXPO_OS === 'web' && typeof window === 'undefined',
    partialize: values, migrate: values,
    merge: (persisted, current) => ({ ...current, ...values(persisted) }),
    onRehydrateStorage: () => state => {
      const language = toLanguage(state?.language);
      changeLanguage(language);
      useLanguagePreferences.setState({ language, hydrated: true });
    },
  },
));
