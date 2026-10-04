import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

export function score(test, result, variant = 'memory') {
  const errors = [];
  const ids = new Set(test.messages.map(m => m.id));
  const valid = result && typeof result.summary === 'string' && typeof result.beforeReply === 'string' &&
    ['observations', 'commitments', 'suggestions', 'memoryUpdates', 'reminderUpdates'].every(key => Array.isArray(result[key])) &&
    result.suggestions.length >= 1 && result.suggestions.length <= 3 && result.suggestions.every(s =>
      (s.action === undefined || s.action === 'reply') ? typeof s.text === 'string' && !!s.text.trim() : s.action === 'no_reply' && typeof s.reason === 'string' && !!s.reason.trim());
  if (!valid) return { contractValid: false, evidenceValid: false, checksPassed: false, errors: ['invalid_contract'] };
  const claims = [...result.observations, ...result.commitments, ...result.memoryUpdates, ...result.reminderUpdates];
  const evidenceValid = claims.every(c => Array.isArray(c.evidenceIds) && c.evidenceIds.length > 0 && c.evidenceIds.every(id => ids.has(id)));
  if (!evidenceValid) errors.push('invalid_evidence_ids');
  const reminders = result.reminderUpdates;
  const expected = test.expected;
  // Memory ablations cannot reference an ID intentionally removed from their input.
  const rule = { ...expected.reminder };
  if (variant === 'no-memory') delete rule.replaceId;
  if (expected.reminder && !reminders.some(item => Object.entries(rule).every(([key, value]) =>
    key === 'textIncludes' ? value.every(word => String(item.text).includes(word)) :
      key === 'dueDate' && value.includes('T') ? String(item[key]).startsWith(value) : item[key] === value))) errors.push('expected_reminder_missing_or_wrong');
  if (expected.noDone && reminders.some(r => r.status === 'done')) errors.push('unsupported_completion');
  if (expected.noConfirmedDebt && reminders.some(r => r.kind === 'commitment' && r.status === 'open')) errors.push('disputed_debt_confirmed');
  if (expected.noConfirmedMeeting && reminders.some(r => r.kind === 'meeting' && r.status === 'open')) errors.push('tentative_meeting_confirmed');
  if (expected.noReminderUpdates && reminders.length) errors.push('draft_saved_as_fact');
  if (expected.hasReply && !result.suggestions.some(s => !s.action || s.action === 'reply')) errors.push('missing_reply');
  if (expected.hasNoReply && !result.suggestions.some(s => s.action === 'no_reply')) errors.push('missing_no_reply');
  const replacements = reminders.map(r => r.replaceId).filter(Boolean);
  if (new Set(replacements).size !== replacements.length) errors.push('duplicate_updates');
  return { contractValid: true, evidenceValid, checksPassed: !errors.length, errors };
}

export function summarize(rows) {
  const groups = {};
  for (const variant of ['memory', 'no-memory']) {
    const selected = rows.filter(row => row.variant === variant);
    const times = selected.filter(r => r.ok).map(r => r.durationMs).sort((a, b) => a - b);
    const count = field => selected.filter(r => r.score?.[field]).length;
    groups[variant] = { attempted: selected.length, successfulRequests: selected.filter(r => r.ok).length,
      contractValid: count('contractValid'), evidenceValid: count('evidenceValid'), checksPassed: count('checksPassed'),
      p50Ms: times.length ? times[Math.ceil(times.length * .5) - 1] : null,
      p95Ms: times.length ? times[Math.ceil(times.length * .95) - 1] : null };
  }
  return groups;
}

async function main() {
  const args = process.argv.slice(2);
  const datasetText = await readFile('benchmarks/cue/cases.json', 'utf8');
  const dataset = JSON.parse(datasetText);
  if (!args.includes('--live')) {
    if (dataset.cases.length < 20 || new Set(dataset.cases.map(c => c.id)).size !== dataset.cases.length) throw Error('Invalid dataset');
    for (const c of dataset.cases) {
      if (!c.messages.length || !c.humanReview && !Object.keys(c.expected).length) throw Error(`Missing assertions: ${c.id}`);
    }
    console.log(`${dataset.cases.length} synthetic cases validated. Pass --live to measure the configured Cue endpoint; no model requests were made.`);
    return;
  }
  const gateway = await readFile('modules/subtext/android/src/main/java/expo/modules/subtext/SupabaseGateway.kt', 'utf8');
  const base = process.env.CUE_EVAL_URL || gateway.match(/const val BASE = "([^"]+)"/)[1];
  const key = process.env.CUE_EVAL_PUBLISHABLE_KEY || gateway.match(/const val PUBLISHABLE_KEY = "([^"]+)"/)[1];
  const auth = await fetch(`${base}/auth/v1/signup`, { method: 'POST', headers: { apikey: key, 'Content-Type': 'application/json' }, body: '{}', signal: AbortSignal.timeout(20000) });
  if (!auth.ok) throw Error(`Evaluation login failed: HTTP ${auth.status}`);
  const session = await auth.json(); // In memory only. Never save or print tokens.
  if (!session.access_token) throw Error('No evaluation session');
  const hash = text => createHash('sha256').update(text).digest('hex');
  const report = { version: 1, kind: 'live-synthetic-regression', startedAt: new Date().toISOString(),
    endpoint: base, gitCommit: execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
    datasetSha256: hash(datasetText), localPromptSha256: hash(await readFile('supabase/functions/deepseek-analyze/prompt.ts', 'utf8')),
    deployedPromptVerified: false, providerModelVerified: false, expectedModelAlias: 'deepseek-flash',
    limitations: 'Synthetic regression suite, not a held-out population benchmark. Automated checks are partial; semantic grounding and style require human review. Timings measure gateway calls, not phone/keyboard latency. Local prompt hash does not prove deployed prompt equality.',
    rows: [] };
  const out = args.includes('--out') ? args[args.indexOf('--out') + 1] : 'benchmarks/cue/report.json';
  const jobs = dataset.cases.flatMap(test => [ { test, variant: 'memory' }, ...(test.compareWithoutMemory ? [{ test, variant: 'no-memory' }] : []) ]);
  const limit = args.includes('--limit') ? Number(args[args.indexOf('--limit') + 1]) : jobs.length;
  let stopped = false;
  for (const { test, variant } of jobs.slice(0, limit)) {
    const body = { messages: test.messages, draft: test.draft, memoryOnly: false,
      personMemory: variant === 'memory' ? test.personMemory : { now: test.personMemory.now, timezone: test.personMemory.timezone, reminders: [], relationship: [] } };
    const start = performance.now(); let row;
    try {
      const response = await fetch(`${base}/functions/v1/deepseek-analyze`, { method: 'POST',
        headers: { apikey: key, Authorization: `Bearer ${session.access_token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(body), signal: AbortSignal.timeout(90000) });
      const result = await response.json();
      row = { id: test.id, variant, ok: response.ok, status: response.status, durationMs: Math.round(performance.now() - start),
        score: response.ok ? score(test, result, variant) : null, result: response.ok ? result : { error: result.error || 'request_failed' }, humanReview: null };
      if ([401, 402, 403, 429, 503].includes(response.status)) stopped = true;
    } catch (error) {
      row = { id: test.id, variant, ok: false, durationMs: Math.round(performance.now() - start), score: null, error: error.name, humanReview: null };
    }
    report.rows.push(row); report.summary = summarize(report.rows);
    report.completedAt = new Date().toISOString(); report.complete = report.rows.length === jobs.length;
    await mkdir('benchmarks/cue', { recursive: true }); await writeFile(out, JSON.stringify(report, null, 2) + '\n');
    console.log(`${report.rows.length}/${Math.min(limit, jobs.length)} ${test.id} ${variant}: ${row.ok ? row.score.checksPassed ? 'checks passed' : row.score.errors.join(', ') : `HTTP ${row.status || row.error}`} (${row.durationMs} ms)`);
    if (stopped) break; // Never manufacture new identities to evade an exhausted quota.
  }
  console.log(JSON.stringify(report.summary));
  if (stopped || report.rows.some(row => !row.ok)) process.exitCode = 1;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main().catch(error => { console.error(error.message); process.exitCode = 1; });
