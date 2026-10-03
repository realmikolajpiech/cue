import fs from 'node:fs';
import assert from 'node:assert/strict';
const path = process.argv[2];
assert(path, 'Usage: node scripts/check-benchmark.mjs <report.json>');
const report = JSON.parse(fs.readFileSync(path, 'utf8'));
const corpus = JSON.parse(fs.readFileSync(new URL('../benchmarks/cases.json', import.meta.url), 'utf8'));
assert.equal(report.schemaVersion, 1);
assert.equal(report.cases.length, 40);
assert.equal(new Set(report.cases.map(row => row.id)).size, 40);
for (const expected of corpus.cases) {
  const row = report.cases.find(row => row.id === expected.id);
  assert(row, `Missing ${expected.id}`);
  assert.equal(row.expectedHigh, expected.expectedHigh);
  assert(['low', 'medium', 'high', 'uncertain'].includes(row.risk));
  assert(typeof row.valid === 'boolean');
  assert(Number.isFinite(row.latencyMs) && row.latencyMs >= 0);
}
assert.match(report.modelSha256, /^[0-9a-f]{64}$/);
assert(['guardian-pl-v2', 'guardian-pl-v3-evidence'].includes(report.promptVersion));
assert.equal(report.runtimeVersion, '0.15.0');
assert.equal(report.metrics.tp + report.metrics.fp + report.metrics.fn + report.metrics.tn, 40);
console.log('Complete benchmark report; inspect quality metrics and device conditions before acceptance.');
