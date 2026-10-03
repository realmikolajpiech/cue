import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

// A debug build only. The key goes through stdin, not command arguments or APK assets.
const source = readFileSync(new URL('../.env.subtext.local', import.meta.url), 'utf8');
const key = source.match(/^DEEPSEEK_API_KEY=(.+)$/m)?.[1]?.trim();
if (!key) throw new Error('Add DEEPSEEK_API_KEY to .env.subtext.local first.');
const result = spawnSync('adb', ['shell', 'run-as', 'com.mikolajpiech.guardian', 'sh', '-c', '"cat > files/subtext-key.import"'], { input: key, encoding: 'utf8' });
if (result.status !== 0) throw new Error('Key provisioning requires an installed debuggable build. Use the API key field in Settings for release builds.');
console.log('Key staged in app-private storage. Restart Cue to import it into Android Keystore.');
