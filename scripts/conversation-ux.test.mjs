import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
import { createInstance } from 'i18next';
import { withDeadline } from '../src/services/deadline.ts';

const i18n = createInstance();
await i18n.init({ lng: 'pl', initAsync: false, resources: Object.fromEntries(['pl', 'en'].map(language => [language, {
  translation: JSON.parse(readFileSync(new URL(`../src/i18n/locales/${language}.json`, import.meta.url), 'utf8')),
}])) });
const source = readFileSync(new URL('../src/features/subtext/conversationPresentation.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
const exports = {};
new Function('require', 'exports', compiled)(name => {
  if (name === '@/i18n') return { t: (key, values) => i18n.t(key, values), dateLocale: () => i18n.language === 'pl' ? 'pl-PL' : 'en-GB' };
  throw new Error(`Unexpected test dependency: ${name}`);
}, exports);
const { filterConversations, initials, conversationTime, messageText } = exports;

const rooms = [
  { id: '1', name: 'Łukasz Żółć', network: 'messenger', updatedAt: 10 },
  { id: '2', name: 'Anna Nowak', network: 'whatsapp', updatedAt: 30 },
  { id: '3', name: 'Łukasz Kowalski', network: 'whatsapp', updatedAt: 20 },
];
test('finds Polish names without accents, regardless of word order, within the selected messenger', () => {
  assert.deepEqual(filterConversations(rooms, 'zolc LUKASZ', 'all').map(room => room.id), ['1']);
  assert.deepEqual(filterConversations(rooms, 'lukasz', 'whatsapp').map(room => room.id), ['3']);
  assert.deepEqual(filterConversations(rooms, 'missing', 'all'), []);
});
test('returns recent conversations without mutating the cached inbox', () => {
  assert.deepEqual(filterConversations(rooms, '  ', 'all').map(room => room.id), ['2', '3', '1']);
  assert.deepEqual(rooms.map(room => room.id), ['1', '2', '3']);
});
test('initials handle whitespace, single names, and Unicode code points', () => {
  assert.equal(initials('  Anna   Nowak '), 'AN');
  assert.equal(initials('Łukasz'), 'ŁU');
  assert.equal(initials(''), '?');
  assert.equal(initials('💬 Cue'), '💬C');
});
test('dates distinguish yesterday across a month boundary and suppress unknown timestamps', () => {
  assert.equal(conversationTime(0), '');
  assert.equal(conversationTime(NaN), '');
  assert.equal(conversationTime(new Date(2026, 8, 30, 12).getTime(), new Date(2026, 9, 1, 9)), 'Wczoraj');
});
test('conversation dates and photo captions follow the selected language', async () => {
  await i18n.changeLanguage('en');
  try {
    assert.equal(conversationTime(new Date(2026, 8, 30, 12).getTime(), new Date(2026, 9, 1, 9)), 'Yesterday');
    assert.equal(messageText('[Zdjęcie] hello'), i18n.t('inbox.photoSentWithCaption', { caption: 'hello' }));
  } finally { await i18n.changeLanguage('pl'); }
});
test('a native request that never settles becomes a retryable error', async () => {
  await assert.rejects(withDeadline(new Promise(() => {}), 10, 'Retry sync'), /Retry sync/);
  assert.equal(await withDeadline(Promise.resolve('cached'), 100, 'Retry sync'), 'cached');
  await assert.rejects(withDeadline(Promise.reject(new Error('Disconnected')), 100, 'Retry sync'), /Disconnected/);
});
