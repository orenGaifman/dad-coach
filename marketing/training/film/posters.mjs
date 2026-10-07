// node film/posters.mjs (from marketing/training, marketing/ served on 127.0.0.1:8788) -> release/training/v1/father-<slug>.jpg
// and ../ad/release/dad-coach-ad-v1.jpg: one frame of each film (720x1280) without the caption - a poster shows the
// screen, not half a sentence. Frames are named by beat + seconds into it, so they follow a retimed voice.
import { chromium } from 'playwright';
import { open } from './pages.mjs';
const SHOTS = [['welcome', 'w05', 6.0], ['book-a-session', 'b04', 3.0], ['reminders', 'r02', 5.0], ['when-cancelled', 'c03', 4.0],
  ['my-dashboard', 'd03', 4.0], ['ad', 'a10', 5.0]];
const browser = await chromium.launch();
for (const [v, id, lt] of SHOTS) await shot(v, id, lt);
await browser.close();

// the local server now and then resets a connection: a frame with a failed load is taken again
async function shot(v, id, lt, tries = 3) {
  const { page, errors } = await open(browser, v, 2 / 3);
  const beat = await page.evaluate((id) => window.TIMELINE.beats.find((b) => b.id === id), id);
  await page.evaluate((t) => window.renderAt(t), beat.start + lt);
  await page.evaluate(() => document.querySelectorAll('.caption').forEach((e) => e.remove()));
  const path = v === 'ad' ? '../ad/release/dad-coach-ad-v1.jpg' : `release/training/v1/father-${v}.jpg`;
  await page.screenshot({ path, type: 'jpeg', quality: 80 });
  await page.close();
  if (errors.length && tries > 1) return shot(v, id, lt, tries - 1);
  if (errors.length) throw new Error(v + ': ' + errors.join(' | '));
  console.log(path);
}
