import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const path = process.argv[2];
assert(path, 'Usage: node scripts/check-inference-protocols.mjs <report.json> [--allow-incomplete]');
const report = JSON.parse(readFileSync(path, 'utf8'));
assert.equal(report.schemaVersion, 1);
if (!process.argv.includes('--allow-incomplete')) assert.equal(report.complete, true, 'Experiment did not finish');
const risks = new Set(['low', 'medium', 'high', 'uncertain']);
const summaries = report.rounds.map(round => {
  if (report.complete) assert.equal(round.cases.length, report.fixtureCount ?? 6);
  let tp = 0, fp = 0, fn = 0, tn = 0, uncertain = 0;
  for (const [index, row] of round.cases.entries()) {
    assert.equal(row.case, index);
    assert.equal(typeof row.expectedHigh, 'boolean');
    assert.equal(typeof row.valid, 'boolean');
    assert(Number.isFinite(row.latencyMs) && row.latencyMs > 0);
    assert.equal(typeof row.syntheticOutput, 'string');
    assert(row.syntheticOutput.length <= 4096);
    if (row.valid) {
      assert(risks.has(row.assessment.risk));
      assert.equal(row.failure, null);
    } else {
      assert.equal(row.assessment, null);
      assert.equal(typeof row.failure, 'string');
    }
    const high = row.assessment?.risk === 'high';
    if (row.expectedHigh) high ? tp++ : fn++;
    else high ? fp++ : tn++;
    if (row.assessment?.risk === 'uncertain') uncertain++;
  }
  const times = round.cases.map(row => row.latencyMs).sort((a, b) => a - b);
  const mean = values => values.length ? Math.round(values.reduce((a, b) => a + b, 0) / values.length) : null;
  return {
    protocol: round.protocol,
    cases: times.length,
    meanMs: mean(times),
    maxMs: times.at(-1) ?? null,
    firstChunkMeanMs: mean(round.cases.filter(row => row.firstChunkMs >= 0).map(row => row.firstChunkMs)),
    valid: round.cases.filter(row => row.valid).length,
    under3Seconds: round.cases.filter(row => row.latencyMs <= 3000).length,
    tp, fp, fn, tn, uncertain,
  };
});
console.log(`${report.device}; ${report.language ?? 'polish'}; ${report.backend}; complete=${report.complete}`);
console.table(summaries);
console.log('Warning metrics use high risk as the positive label; invalid/uncertain scam assessments count as misses. Schema acceptance is not accuracy. These development cases do not establish production quality, worst-case latency or battery use.');
