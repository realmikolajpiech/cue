import { existsSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const env = { ...process.env };
// Use the working Android toolchain on macOS, even if Homebrew's latest Java is default.
if (process.platform === 'darwin') {
  const java17 = '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home';
  if (existsSync(java17)) env.JAVA_HOME = java17;
}
if (!env.ANDROID_HOME) {
  const sdk = env.ANDROID_SDK_ROOT || join(homedir(), process.platform === 'darwin' ? 'Library/Android/sdk' : 'Android/Sdk');
  if (existsSync(sdk)) env.ANDROID_HOME = sdk;
}
if (env.JAVA_HOME) env.PATH = `${join(env.JAVA_HOME, 'bin')}${process.platform === 'win32' ? ';' : ':'}${env.PATH || ''}`;

const result = spawnSync(process.execPath, ['node_modules/expo/bin/cli', 'run:android', ...process.argv.slice(2)], {
  env,
  stdio: 'inherit',
});
if (result.error) console.error(result.error.message);
process.exit(result.status ?? 1);
