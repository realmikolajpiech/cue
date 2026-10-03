import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { setImmediate } from 'node:timers/promises';
import test from 'node:test';
import ts from 'typescript';

// Exercise the production sync function with real TanStack observers, without a native UI.
const require = createRequire(import.meta.url);
const { QueryClient, QueryObserver, focusManager, onlineManager } = require('@tanstack/react-query');
const source = readFileSync(new URL('../src/services/guardianSync.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;
const exports = {};
new Function('require', 'exports', compiled)(require, exports);
const { attachGuardianSync } = exports;
async function flush() { await setImmediate(); await setImmediate(); }

async function fixture() {
  focusManager.setFocused(true);
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, gcTime: Infinity, retry: false, networkMode: 'always' } } });
  client.mount();
  const reads = { status: 0, results: 0 };
  const unsubscribe = Object.keys(reads).map(key => {
    const observer = new QueryObserver(client, { queryKey: ['guardian', key], queryFn: async () => ++reads[key] });
    return observer.subscribe(() => {});
  });
  let nativeListener;
  let appListener;
  const appState = {
    currentState: 'active',
    addEventListener(_event, listener) {
      appListener = listener;
      return { remove() { appListener = undefined; } };
    },
  };
  const detach = attachGuardianSync(client, listener => {
    nativeListener = listener;
    return { remove() { nativeListener = undefined; } };
  }, appState, false);
  await flush();
  return {
    client, reads,
    async emit(change) { nativeListener?.(change); await flush(); },
    async state(state) { appState.currentState = state; appListener?.(state); await flush(); },
    close() { detach(); unsubscribe.forEach(remove => remove()); client.unmount(); client.clear(); focusManager.setFocused(undefined); },
  };
}

test('status changes avoid history reads; a saved result refreshes both immediately', async () => {
  const f = await fixture();
  try {
    assert.deepEqual(f.reads, { status: 1, results: 1 });
    await f.emit({ resultsChanged: false });
    assert.deepEqual(f.reads, { status: 2, results: 1 });
    await f.emit({ resultsChanged: true });
    assert.deepEqual(f.reads, { status: 3, results: 2 });
  } finally { f.close(); }
});

test('background events mark data stale without reads; resume fetches the latest result once', async () => {
  const f = await fixture();
  try {
    await f.state('background');
    for (let i = 0; i < 20; i++) await f.emit({ resultsChanged: i % 2 === 0 });
    assert.deepEqual(f.reads, { status: 1, results: 1 });
    assert.equal(f.client.getQueryState(['guardian', 'results']).isInvalidated, true);
    await f.state('active');
    assert.deepEqual(f.reads, { status: 2, results: 2 });
  } finally { f.close(); }
});

test('an older native build still refreshes results with empty or missing event payloads', async () => {
  const f = await fixture();
  try {
    await f.emit({});
    await f.emit();
    assert.deepEqual(f.reads, { status: 3, results: 3 });
  } finally { f.close(); }
});

test('local reads stay responsive without internet', async () => {
  onlineManager.setOnline(false);
  const f = await fixture();
  try {
    await f.emit({ resultsChanged: true });
    assert.deepEqual(f.reads, { status: 2, results: 2 });
  } finally { f.close(); onlineManager.setOnline(true); }
});
