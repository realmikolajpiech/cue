import assert from 'node:assert/strict';
import test from 'node:test';
import { filterConversations, initials, conversationTime } from '../src/features/subtext/conversationPresentation.ts';
import { withDeadline } from '../src/services/deadline.ts';

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
test('a native request that never settles becomes a retryable error', async () => {
  await assert.rejects(withDeadline(new Promise(() => {}), 10, 'Retry sync'), /Retry sync/);
  assert.equal(await withDeadline(Promise.resolve('cached'), 100, 'Retry sync'), 'cached');
  await assert.rejects(withDeadline(Promise.reject(new Error('Disconnected')), 100, 'Retry sync'), /Disconnected/);
});
