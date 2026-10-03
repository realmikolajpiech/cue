import { createInstance } from 'i18next';
import { initReactI18next, useTranslation } from 'react-i18next';
import pl from './locales/pl.json';
import en from './locales/en.json';

export type Language = 'pl' | 'en';
export const languages: { code: Language; label: string }[] = [
  { code: 'pl', label: 'Polski' },
  { code: 'en', label: 'English' },
];

const i18next = createInstance();

// Source-text keys keep the Polish fallback readable, including persisted demo data.
void i18next.use(initReactI18next).init({
  resources: { pl: { translation: pl }, en: { translation: en } },
  lng: 'pl', fallbackLng: 'pl', supportedLngs: ['pl', 'en'],
  keySeparator: false, nsSeparator: false, initAsync: false,
  interpolation: { escapeValue: false }, react: { useSuspense: false },
});

export function t(key: string, values?: Record<string, string | number>): string {
  return i18next.t(key, values ?? {});
}

export function useLanguage() {
  const { i18n } = useTranslation();
  return i18n.resolvedLanguage === 'en' ? 'en' : 'pl';
}

export function changeLanguage(language: Language) {
  void i18next.changeLanguage(language);
}

export function dateLocale() {
  return i18next.resolvedLanguage === 'en' ? 'en-GB' : 'pl-PL';
}
