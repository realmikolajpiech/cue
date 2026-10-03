import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';
import ts from 'typescript';

const require = createRequire(import.meta.url);
function load(path, imports = {}) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  new Function('require', 'exports', compiled)(name => imports[name] ?? require(name), exports);
  return exports;
}
const types = load('../src/types/guardian.ts');
const { toThreat, isWarning, analysisTitle } = load('../src/features/protection/presentation.ts', { '@/types/guardian': types });
const fixture = {
  schemaVersion: 1, analysisVersion: types.CURRENT_ANALYSIS_VERSION, id: '00000000-0000-4000-8000-000000000001', createdAt: 1000,
  sourceApp: 'SMS', risk: 'high', category: 'credential_theft', signals: ['credential_request'],
  explanation: 'Prośba o kod.', recommendedAction: 'Nie udostępniaj kodu.', analysisSource: 'on_device', reviewStatus: 'new',
};
test('only elevated risk appears among warnings, including reviewed warnings', () => {
  for (const risk of ['low', 'uncertain', 'medium', 'high']) {
    for (const reviewStatus of ['new', 'reviewed']) {
      assert.equal(isWarning(toThreat({ ...fixture, risk, reviewStatus })), risk === 'high' || risk === 'medium');
    }
  }
  assert.equal(isWarning(toThreat({ ...fixture, analysisVersion: 'old-version' })), false);
});
test('harmless and failed analyses do not display a generic threat category', () => {
  assert.equal(analysisTitle({ ...fixture, risk: 'low', category: 'unknown' }), 'Nie wykryto zagrożenia');
  assert.equal(analysisTitle({ ...fixture, risk: 'uncertain', category: 'unknown' }), 'Nie udało się ocenić ryzyka');
});
test('notification context survives validation and mapping; old results remain readable', () => {
  assert.equal(types.resultSchema.safeParse(fixture).success, true);
  assert.equal(toThreat(fixture).notificationContext, undefined);
  const notificationContext = { title: 'Testowy nadawca', receivedAt: 2000, messages: [{ sender: 'Nadawca', text: 'Podaj kod.' }] };
  const parsed = types.resultSchema.parse({ ...fixture, notificationContext });
  assert.deepEqual(toThreat(parsed).notificationContext, notificationContext);
  assert.equal(toThreat(parsed).signals[0], 'Prośba o hasło lub kod');
  assert.equal(types.resultSchema.safeParse({ ...fixture, notificationContext: { ...notificationContext, messages: [{ sender: '', text: 'x'.repeat(302) }] } }).success, false);
  assert.equal(types.resultSchema.safeParse({ ...fixture, notificationContext: { ...notificationContext, messages: [] } }).success, false);
});
