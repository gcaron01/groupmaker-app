const sharp = require('sharp');
const path = require('path');

const src = 'c:/xampp/htdocs/groupmaker/docs/groupmaker_logo_app.png';
const base = 'C:/dev/groupmaker-app/android/app/src/main/res';

const sizes = {
  'mipmap-mdpi': 48,
  'mipmap-hdpi': 72,
  'mipmap-xhdpi': 96,
  'mipmap-xxhdpi': 144,
  'mipmap-xxxhdpi': 192,
};

const fgSizes = {
  'mipmap-mdpi': 108,
  'mipmap-hdpi': 162,
  'mipmap-xhdpi': 216,
  'mipmap-xxhdpi': 324,
  'mipmap-xxxhdpi': 432,
};

async function main() {
  // Standard launcher icons
  for (const [folder, size] of Object.entries(sizes)) {
    const outDir = path.join(base, folder);

    await sharp(src).resize(size, size, { fit: 'contain' }).png().toFile(path.join(outDir, 'ic_launcher.png'));
    console.log(`${folder}/ic_launcher.png -> ${size}x${size}`);

    await sharp(src).resize(size, size, { fit: 'contain' }).png().toFile(path.join(outDir, 'ic_launcher_round.png'));
    console.log(`${folder}/ic_launcher_round.png -> ${size}x${size}`);
  }

  // Foreground for adaptive icons (108dp canvas, icon in 66dp safe zone)
  for (const [folder, size] of Object.entries(fgSizes)) {
    const outDir = path.join(base, folder);
    const iconSize = Math.round(size * 66 / 108);
    const offset = Math.round((size - iconSize) / 2);

    // Resize icon to safe zone size
    const resized = await sharp(src).resize(iconSize, iconSize, { fit: 'contain' }).png().toBuffer();

    // Create transparent canvas and composite icon centered
    await sharp({
      create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } }
    })
      .composite([{ input: resized, left: offset, top: offset }])
      .png()
      .toFile(path.join(outDir, 'ic_launcher_foreground.png'));

    console.log(`${folder}/ic_launcher_foreground.png -> ${size}x${size} (icon ${iconSize})`);
  }

  // Play Store icon 512x512
  await sharp(src).resize(512, 512, { fit: 'contain' }).png().toFile('C:/dev/groupmaker-app/store_icon_512.png');
  console.log('store_icon_512.png -> 512x512');

  console.log('DONE');
}

main().catch(console.error);
