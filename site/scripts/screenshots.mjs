// Screenshots of the built site for review and the hub page (Playwright, Chromium).
// Usage: npm run build && npm run preview  (in another shell), then
//   PLAYWRIGHT=~/repos/big-boss-ad/node_modules/playwright/index.mjs node scripts/screenshots.mjs [baseUrl]
// Writes site/screenshots/{home,demo}-{390,1440}.png and prints layout checks (RTL, horizontal overflow).
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { homedir } from 'node:os';

const pw = await import(process.env.PLAYWRIGHT || `${homedir()}/repos/big-boss-ad/node_modules/playwright/index.mjs`);
const base = process.argv[2] || 'http://localhost:4175';
const out = fileURLToPath(new URL('../screenshots/', import.meta.url));
mkdirSync(out, { recursive: true });

const browser = await pw.chromium.launch();
for (const [w, h] of [[390, 844], [1440, 900]]) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1, reducedMotion: 'reduce' });
  await page.goto(`${base}/?demo=static`, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  const checks = await page.evaluate(() => ({
    dir: document.documentElement.dir,
    overflowX: document.documentElement.scrollWidth - window.innerWidth,
    wide: [...document.querySelectorAll('body *')].filter((n) => n.getBoundingClientRect().right > window.innerWidth + 1
      && getComputedStyle(n).position !== 'fixed' && !n.closest('.demo-tabs, .visually-hidden')).slice(0, 5).map((n) => n.className || n.tagName),
  }));
  console.log(`${w}px`, JSON.stringify(checks));
  await page.screenshot({ path: `${out}home-${w}.png` });
  await page.addStyleTag({ content: '.site-header { position: static !important; }' }); // keep it out of element shots
  await page.locator('#demo').scrollIntoViewIfNeeded();
  await page.locator('[data-scenario="session"]').click();
  await page.locator('#demo').screenshot({ path: `${out}demo-${w}.png` });
  await page.close();
}
await browser.close();
