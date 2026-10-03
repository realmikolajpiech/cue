// Resize the approved source art into platform assets without changing its design.
const fs = require('node:fs/promises');
const path = require('node:path');
const { generateImageAsync, generateImageBackgroundAsync, compositeImagesAsync } = require('@expo/image-utils');
const { PNG } = require('pngjs');
const projectRoot = path.resolve(__dirname, '..');

async function resize(src, size, options = {}) {
  const { source } = await generateImageAsync({ projectRoot }, { src: path.join(projectRoot, src), width: size, height: size, resizeMode: 'contain', ...options });
  return source;
}

async function main() {
  await fs.writeFile(path.join(projectRoot, 'assets/icon.png'), await resize('assets/cue-logo.png', 1024, { removeTransparency: true }));
  await fs.writeFile(path.join(projectRoot, 'assets/favicon.png'), await resize('assets/cue-logo.png', 64));
  await fs.writeFile(path.join(projectRoot, 'assets/splash-icon.png'), await resize('assets/cue-logo.png', 512));
  // Keep the whole illustration inside the adaptive icon's central safe area.
  const foreground = await compositeImagesAsync({
    foreground: await resize('assets/cue-mascot-cutout.png', 600),
    background: await generateImageBackgroundAsync({ width: 1024, height: 1024, resizeMode: 'contain', backgroundColor: 'transparent' }),
    x: 212, y: 212,
  });
  await fs.writeFile(path.join(projectRoot, 'assets/android-icon-foreground.png'), foreground);
  // Android themed icons consume only this alpha mask.
  const monochrome = PNG.sync.read(foreground);
  for (let index = 0; index < monochrome.data.length; index += 4) {
    monochrome.data[index] = 255; monochrome.data[index + 1] = 255; monochrome.data[index + 2] = 255;
  }
  await fs.writeFile(path.join(projectRoot, 'assets/android-icon-monochrome.png'), PNG.sync.write(monochrome));
  await fs.writeFile(path.join(projectRoot, 'assets/android-icon-background.png'), await generateImageBackgroundAsync({ width: 1024, height: 1024, resizeMode: 'contain', backgroundColor: '#4869B3' }));
  await fs.writeFile(path.join(projectRoot, 'modules/subtext/android/src/main/res/drawable/cue_brand.png'), await resize('assets/cue-logo.png', 144));
  console.log('Cue launcher, splash, favicon, adaptive, themed and native brand assets generated.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
