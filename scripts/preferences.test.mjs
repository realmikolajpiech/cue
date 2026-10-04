import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';
import ts from 'typescript';

const require = createRequire(import.meta.url);
function load(file, entries, mocks = {}) {
  mocks = { '@/services/subtext': { syncNativeLanguage: () => {} }, ...mocks };
  const items = new Map(entries);
  const storage = {
    getItem: async key => items.get(key) ?? null,
    setItem: async (key, value) => { items.set(key, value); },
    removeItem: async key => { items.delete(key); },
  };
  const source = readFileSync(new URL(file, import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  new Function('require', 'exports', compiled)(name => name in mocks ? mocks[name] : name === '@react-native-async-storage/async-storage' ? { __esModule: true, default: storage } : require(name), exports);
  return { ...exports, items };
}

test('appearance cleanup preserves the existing dark preference without loading old demo data', async () => {
  const { useAppearance, items } = load('../src/theme/preferences.ts', [
    ['cue-appearance-v1', JSON.stringify({ state: { dark: true }, version: 0 })],
  ]);
  await useAppearance.persist.rehydrate();
  assert.equal(useAppearance.getState().hydrated, true);
  assert.equal(useAppearance.getState().dark, true);
  useAppearance.getState().toggleTheme();
  assert.deepEqual(JSON.parse(items.get('cue-appearance-v1')).state, { dark: false });
});

test('developer preference upgrades without carrying over old onboarding or language state', async () => {
  const { usePreferences, items } = load('../src/features/preferences.ts', [
    ['guardian-preferences-v1', JSON.stringify({ state: { developerMode: true, onboarded: true, language: 'en' }, version: 2 })],
  ]);
  await usePreferences.persist.rehydrate();
  assert.equal(usePreferences.getState().developerMode, true);
  assert.equal('onboarded' in usePreferences.getState(), false);
  assert.equal('language' in usePreferences.getState(), false);
  usePreferences.getState().toggleDeveloperMode();
  assert.deepEqual(JSON.parse(items.get('cue-preferences-v1')).state, { developerMode: false, demoMode: false, demoNames: {} });
});

test('the current Cue developer preference takes priority over the upgrade fallback', async () => {
  const { usePreferences } = load('../src/features/preferences.ts', [
    ['cue-preferences-v1', JSON.stringify({ state: { developerMode: false }, version: 2 })],
    ['guardian-preferences-v1', JSON.stringify({ state: { developerMode: true }, version: 2 })],
  ]);
  await usePreferences.persist.rehydrate();
  assert.equal(usePreferences.getState().developerMode, false);
});

test('language upgrades independently without restoring retired onboarding data', async () => {
  let activeLanguage = 'pl';
  const { useLanguagePreferences, items } = load('../src/i18n/preferences.ts', [
    ['guardian-preferences-v1', JSON.stringify({ state: { language: 'en', onboarded: true }, version: 2 })],
  ], { './index': { deviceLanguage: () => 'pl', changeLanguage: language => { activeLanguage = language; } } });
  await useLanguagePreferences.persist.rehydrate();
  assert.equal(useLanguagePreferences.getState().hydrated, true);
  assert.equal(activeLanguage, 'en');
  assert.equal('onboarded' in useLanguagePreferences.getState(), false);
  useLanguagePreferences.getState().setLanguage('pl');
  assert.equal(activeLanguage, 'pl');
  assert.deepEqual(JSON.parse(items.get('cue-language-v1')).state, { language: 'pl' });
});

test('the Cue language preference takes priority over the old key', async () => {
  const { useLanguagePreferences } = load('../src/i18n/preferences.ts', [
    ['cue-language-v1', JSON.stringify({ state: { language: 'pl' }, version: 1 })],
    ['guardian-preferences-v1', JSON.stringify({ state: { language: 'en' }, version: 2 })],
  ], { './index': { deviceLanguage: () => 'en', changeLanguage: () => {} } });
  await useLanguagePreferences.persist.rehydrate();
  assert.equal(useLanguagePreferences.getState().language, 'pl');
});
