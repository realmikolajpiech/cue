import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const path = process.argv[2];
assert(path, 'Usage: node scripts/check-inference-latency.mjs <guardian-latency-threads.json>');
const report = JSON.parse(readFileSync(path, 'utf8'));
assert.equal(report.schemaVersion, 1);
assert.equal(report.complete, true, 'The hardware comparison did not finish');
assert.equal(report.outputsIdentical, true, 'A configuration changed the model outputs');
assert.equal(report.rounds.length, 4);
const expectedThreads = [null, 2, 6, null];
const summaries = report.rounds.map((round, roundIndex) => {
  assert.equal(round.cpuThreads, expectedThreads[roundIndex]);
  assert.equal(round.cases.length, 4);
  round.cases.forEach((row, caseIndex) => {
    assert.equal(row.case, caseIndex);
    assert.equal(row.matchesBaseline, true);
    assert(Number.isFinite(row.latencyMs) && row.latencyMs > 0);
    assert.equal(row.valid, report.rounds[0].cases[caseIndex].valid);
    assert.equal(row.failure, report.rounds[0].cases[caseIndex].failure);
    assert.deepEqual(row.assessment, report.rounds[0].cases[caseIndex].assessment);
  });
  const times = round.cases.map(row => row.latencyMs).sort((a, b) => a - b);
  return {
    configuration: round.cpuThreads === null ? `default (${roundIndex === 0 ? 'start' : 'end'})` : `${round.cpuThreads} CPU threads`,
    medianMs: (times[1] + times[2]) / 2,
    meanMs: Math.round(times.reduce((a, b) => a + b, 0) / times.length),
    valid: round.cases.filter(row => row.valid).length,
    initializationMs: round.initializationMs,
  };
});
console.log(`${report.device}; ${report.promptVersion}; unchanged assessments and validation failures across all rounds`);
console.table(summaries);
console.log('Valid counts schema/citation acceptance, not correct classification. Matching failures are not successful assessments. Compare each case and both default rounds before selecting a setting. This four-case experiment does not establish production accuracy or battery consumption.');
