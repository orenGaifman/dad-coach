// Screenshots of the built site for review and the hub page (Playwright, Chromium).
// Usage: npm run build && npm run preview  (in another shell), then
//   PLAYWRIGHT=~/repos/big-boss-ad/node_modules/playwright/index.mjs node scripts/screenshots.mjs [baseUrl]
// Writes site/screenshots/home-390.png (first screen), demo-1440.png (the demo), full-{390,1440}.jpg (the whole
// page, static: reduced motion + ?demo=static) and prints layout checks (RTL, horizontal overflow, console errors).
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
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(`${base}/?demo=static`, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  // walk the page once so every lazy image is in before the full-page shot
  await page.evaluate(async () => {
    for (let y = 0; y < document.body.scrollHeight; y += 600) { window.scrollTo(0, y); await new Promise((r) => setTimeout(r, 60)); }
    window.scrollTo(0, 0);
  });
  await page.waitForLoadState('networkidle');
  const checks = await page.evaluate(() => ({
    dir: document.documentElement.dir,
    overflowX: document.documentElement.scrollWidth - window.innerWidth,
    wide: [...document.querySelectorAll('body *')].filter((n) => n.getBoundingClientRect().right > window.innerWidth + 1
      && getComputedStyle(n).position !== 'fixed' && !n.closest('.demo-tabs, .visually-hidden')).slice(0, 5).map((n) => n.className || n.tagName),
  }));
  console.log(`${w}px`, JSON.stringify({ ...checks, errors }));
  if (w === 390) await page.screenshot({ path: `${out}home-390.png` });
  await page.screenshot({ path: `${out}full-${w}.jpg`, fullPage: true, type: "jpeg", quality: 78 }); // jpeg: the full page is tall
  if (w === 1440) {
    await page.addStyleTag({ content: '.site-header { position: static !important; }' }); // keep it out of element shots
    await page.locator('#demo').scrollIntoViewIfNeeded();
    await page.locator('[data-scenario="session"]').click();
    await page.locator('#demo').screenshot({ path: `${out}demo-1440.png` });
  }
  await page.close();
}
await browser.close();
