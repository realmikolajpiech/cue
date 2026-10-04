import { i18n } from './index';

export { dateLocale, useLanguage } from './index';

// Guardian-era screens use Polish source text as keys, which may contain dots and colons.
export function t(key: string, values?: Record<string, string | number>): string {
  return i18n.t(key, { ...values, ns: 'legacy', keySeparator: false, nsSeparator: false, defaultValue: key });
}
