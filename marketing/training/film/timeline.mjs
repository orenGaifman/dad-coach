// node training/film/timeline.mjs <video> -> out/<video>_timeline.json (voice and sound cue times for training/audio/mix.py)
import { chromium } from 'playwright';
import { writeFileSync, mkdirSync } from 'fs';
import { open } from './pages.mjs';
const v = process.argv[2];
mkdirSync('out', { recursive: true });
const browser = await chromium.launch();
const { page, errors } = await open(browser, v);
const tl = await page.evaluate(() => window.TIMELINE);
writeFileSync(`out/${v}_timeline.json`, JSON.stringify(tl, null, 1));
console.log(v, tl.total.toFixed(2) + 's', tl.beats.length, 'beats', errors.length ? 'ERRORS ' + errors.join(' | ') : '');
await browser.close();
if (errors.length) process.exit(1);
