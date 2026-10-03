import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, readFile, rename, rm, stat } from 'node:fs/promises';
import { homedir } from 'node:os';
import path from 'node:path';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = fileURLToPath(new URL('../', import.meta.url));
const model = JSON.parse(await readFile(path.join(root, 'models/catalog/gemma3-1b.json'), 'utf8'));
const target = path.join(root, 'models', model.filename);
const temp = `${target}.part`;
const args = process.argv.slice(2);
if (args.some(arg => !['--push', '--help'].includes(arg))) {
  console.error('Usage: npm run model:download -- [--push]');
  process.exit(1);
}
if (args.includes('--help')) {
  console.log(`Accept Gemma access at ${model.accessUrl}, then set HF_TOKEN locally or use a saved Hugging Face login.\nDownloads and verifies ${model.filename} (${Math.round(model.sizeBytes / 1e6)} MB).\n--push copies the verified model to /sdcard/Download/ on the connected Android device; import it in Guardian.\nANDROID_SERIAL selects a device when multiple are connected. No token is stored in the app.`);
  process.exit(0);
}
async function verified(file) {
  if ((await stat(file).catch(() => null))?.size !== model.sizeBytes) return false;
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex') === model.sha256;
}
try {
  await mkdir(path.dirname(target), { recursive: true });
  if (!await verified(target)) {
    const tokenFile = process.env.HF_TOKEN_PATH ?? path.join(process.env.HF_HOME ?? path.join(homedir(), '.cache/huggingface'), 'token');
    const token = process.env.HF_TOKEN ?? (await readFile(tokenFile, 'utf8').catch(() => '')).trim();
    const url = `https://huggingface.co/${model.repository}/resolve/${model.revision}/${model.filename}`;
    // Fetch strips Authorization on cross-origin redirects to the model CDN.
    const response = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      signal: AbortSignal.timeout(60_000),
    });
    if ([401, 403].includes(response.status)) {
      throw new Error(`Model access denied. Accept the Gemma conditions at ${model.accessUrl}, then configure HF_TOKEN or a local Hugging Face login. Do not paste your token into chat.`);
    }
    if (!response.ok || !response.body) throw new Error(`Download failed (HTTP ${response.status}).`);
    const hash = createHash('sha256');
    let bytes = 0; let lastProgress = 0;
    const monitor = new Transform({ transform(chunk, encoding, callback) {
      bytes += chunk.length;
      if (bytes > model.sizeBytes) return callback(new Error('Unexpected model size.'));
      hash.update(chunk);
      if (Date.now() - lastProgress > 2000) {
        console.log(`Downloading: ${Math.floor(bytes / model.sizeBytes * 100)}%`);
        lastProgress = Date.now();
      }
      callback(null, chunk);
    } });
    await pipeline(Readable.fromWeb(response.body), monitor, createWriteStream(temp, { mode: 0o600 }));
    if (bytes !== model.sizeBytes || hash.digest('hex') !== model.sha256) throw new Error('Model verification failed; the previous file was preserved.');
    await rename(temp, target);
  }
  console.log(`Verified model: ${target}\nSHA-256: ${model.sha256}`);
  if (args.includes('--push')) {
    const result = spawnSync('adb', ['push', target, `/sdcard/Download/${model.filename}`], { stdio: 'inherit' });
    if (result.error || result.status !== 0) throw new Error('Device transfer failed. Check adb devices and ANDROID_SERIAL.');
    console.log('Open Guardian → Ochrona → Importuj model .litertlm → Downloads. Import leaves monitoring off.');
  }
} catch (error) {
  await rm(temp, { force: true });
  console.error(error.message);
  process.exitCode = 1;
}
