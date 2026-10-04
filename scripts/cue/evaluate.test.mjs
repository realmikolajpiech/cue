import test from 'node:test';
import assert from 'node:assert/strict';
import { score, summarize } from './evaluate.mjs';
const sample = { messages: [{ id: 'm1' }], expected: { noDone: true } };
const result = { summary: '', beforeReply: '', observations: [], commitments: [], memoryUpdates: [], reminderUpdates: [], suggestions: [{ action: 'reply', text: 'dzięki' }] };
test('rejects invented evidence even when output shape is correct', () => {
  const scored = score(sample, { ...result, observations: [{ text: 'claim', evidenceIds: ['fake'] }] });
  assert.equal(scored.contractValid, true); assert.equal(scored.evidenceValid, false); assert.equal(scored.checksPassed, false);
});
test('flags unsupported completion and duplicate updates', () => {
  const change = { replaceId: 'r1', status: 'done', evidenceIds: ['m1'] };
  const scored = score(sample, { ...result, reminderUpdates: [change, change] });
  assert.deepEqual(scored.errors, ['unsupported_completion', 'duplicate_updates']);
});
test('failed requests remain in denominator and have no invented latency percentile', () => {
  assert.deepEqual(summarize([{ variant: 'memory', ok: false }]).memory, { attempted: 1, successfulRequests: 0, contractValid: 0, evidenceValid: 0, checksPassed: 0, p50Ms: null, p95Ms: null });
});
test('ablation does not require IDs withheld from model', () => {
  const item = { owner: 'other', evidenceIds: ['m1'], replaceId: '' };
  const c = { ...sample, expected: { reminder: { owner: 'other', replaceId: 'saved-id' } } };
  assert.equal(score(c, { ...result, reminderUpdates: [item] }, 'memory').checksPassed, false);
  assert.equal(score(c, { ...result, reminderUpdates: [item] }, 'no-memory').checksPassed, true);
});
