import { createInstance } from 'i18next';
import { initReactI18next, useTranslation } from 'react-i18next';
import { getLocales } from 'expo-localization';
import pl from './locales/pl.json';
import en from './locales/en.json';
import legacyPl from './locales/legacy/pl.json';
import legacyEn from './locales/legacy/en.json';

export type Language = 'pl' | 'en';
export const languages: { code: Language; label: string }[] = [
  { code: 'pl', label: 'Polski' },
  { code: 'en', label: 'English' },
];

/** Polish for Polish devices, English everywhere else. */
export function deviceLanguage(): Language {
  try { return getLocales()[0]?.languageCode === 'pl' ? 'pl' : 'en'; } catch { return 'en'; }
}

export const i18n = createInstance();

void i18n.use(initReactI18next).init({
  resources: { pl: { translation: pl, legacy: legacyPl }, en: { translation: en, legacy: legacyEn } },
  lng: deviceLanguage(), fallbackLng: 'en', supportedLngs: ['pl', 'en'],
  ns: ['translation', 'legacy'], defaultNS: 'translation',
  initAsync: false, returnNull: false,
  interpolation: { escapeValue: false }, react: { useSuspense: false },
});

/** Non-hook translator for code outside React (errors, alerts built in callbacks). */
export function t(key: string, values?: Record<string, unknown>): string {
  return i18n.t(key, values ?? {});
}

// Hermes on Android lacks Intl.PluralRules, so plural categories are resolved here
// instead of relying on i18next's built-in plural handling.
function pluralCategory(count: number, language: Language) {
  if (language === 'en') return count === 1 ? 'one' : 'other';
  if (count === 1) return 'one';
  const few = count % 10 >= 2 && count % 10 <= 4 && !(count % 100 >= 12 && count % 100 <= 14);
  return few ? 'few' : 'many';
}

/** Translates `key_one` / `key_few` / `key_many` / `key_other` for `count`. */
export function plural(key: string, count: number, values?: Record<string, unknown>): string {
  return i18n.t(`${key}_${pluralCategory(count, currentLanguage())}`, { ...values, count });
}

export function currentLanguage(): Language {
  return i18n.resolvedLanguage === 'pl' ? 'pl' : 'en';
}

export function useLanguage(): Language {
  const { i18n: instance } = useTranslation();
  return instance.resolvedLanguage === 'pl' ? 'pl' : 'en';
}

export function changeLanguage(language: Language) {
  void i18n.changeLanguage(language);
}

export function dateLocale() {
  return currentLanguage() === 'pl' ? 'pl-PL' : 'en-GB';
}

export { useTranslation };
