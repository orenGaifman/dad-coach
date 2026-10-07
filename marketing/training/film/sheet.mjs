// node training/film/sheet.mjs <video> [per-beat=2] -> out/qa/<video>_sheet_<n>.jpg: frames from every beat, labelled,
// to check a whole video at a glance BEFORE rendering it.
import { chromium } from 'playwright';
import { mkdirSync } from 'fs';
import { open, size } from './pages.mjs';
const [v, perArg = '2'] = process.argv.slice(2);
const per = Number(perArg);
mkdirSync('out/qa', { recursive: true });
const browser = await chromium.launch();
const { page, errors } = await open(browser, v);
const tl = await page.evaluate(() => window.TIMELINE);
const shots = [];
for (const b of tl.beats) {
  for (let k = 0; k < per; k++) {
    const t = +(b.start + 0.3 + ((b.dur - 0.5) * (k + 1)) / per).toFixed(2);
    await page.evaluate((t) => window.renderAt(t), t);
    shots.push({ id: b.id, t, img: (await page.screenshot({ type: 'jpeg', quality: 70 })).toString('base64') });
  }
}
const { width, height } = size(v), w = 300, h = Math.round((height / width) * w);
const sheet = await browser.newPage({ viewport: { width: w * 6, height: h * 2 } });
for (let n = 0; n * 12 < shots.length; n++) {
  const part = shots.slice(n * 12, (n + 1) * 12);
  await sheet.setContent(`<body style="margin:0;background:#222;display:grid;grid-template-columns:repeat(6,${w}px)">${part.map((s) =>
    `<div style="position:relative"><img src="data:image/jpeg;base64,${s.img}" style="width:${w}px;height:${h}px;display:block"><span style="position:absolute;left:4px;top:4px;background:#000c;color:#ff0;font:bold 15px monospace;padding:2px 4px">${s.id} ${s.t}</span></div>`).join('')}</body>`);
  await sheet.screenshot({ path: `out/qa/${v}_sheet_${n}.jpg`, type: 'jpeg', quality: 75, clip: { x: 0, y: 0, width: w * 6, height: h * Math.ceil(part.length / 6) } });
}
console.log(v, 'total', tl.total.toFixed(1) + 's', 'beats', tl.beats.length, errors.length ? 'ERRORS ' + errors.join(' | ') : 'no errors');
await browser.close();
