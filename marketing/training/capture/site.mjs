// node training/capture/site.mjs  (from marketing/) -> training/film/assets/screens/site-*.png
// Real captures of the marketing site's start (site/): the hero, the signup card empty, filled with the demo name, and
// its done state. The site is served as plain files (python http.server on :8779 from ../site); the one Vite-only line
// (main.js importing its CSS, and public/ at the root) is answered with the same file minus that import, and the CSS is linked instead.
// With the default config (SIGNUP_ENDPOINT empty) the form sends nothing anywhere - it only shows its done state.
// The brand is written in Hebrew ("דאד קואץ׳"), as the dashboard and WhatsApp write it since 2026-10-07: the site's own
// copy still says "Dad Coach", so the page is served with the name replaced (and "ש-Dad Coach" -> "שדאד קואץ׳").
// Nothing else on the page is changed.
import { chromium } from 'playwright';
import { spawn } from 'child_process';
import { readFileSync } from 'fs';

const OUT = 'training/film/assets/screens';
const server = spawn('python3', ['-m', 'http.server', '8779', '--bind', '127.0.0.1', '-d', '../site'], { stdio: 'ignore' });
await new Promise((r) => setTimeout(r, 800));
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, reducedMotion: 'reduce' });
  await page.route('**/src/main.js', (route) => route.fulfill({
    contentType: 'text/javascript',
    body: readFileSync('../site/src/main.js', 'utf8').replace("import './styles/site.css';", ''),
  }));
  const hebrew = (html) => html.replace(/>([^<]*)</g, (m, text) => '>' + text.replace(/([בהוכלמש])-Dad Coach/g, '$1דאד קואץ׳').replace(/Dad Coach/g, 'דאד קואץ׳') + '<');
  await page.route((u) => u.pathname === '/' || u.pathname === '/index.html', (route) => route.fulfill({
    contentType: 'text/html; charset=utf-8', body: hebrew(readFileSync('../site/index.html', 'utf8')),
  }));
  // Vite serves site/public at the root; a plain file server does not
  await page.route(/\/img\//, (route) => route.fulfill({ path: '../site/public' + new URL(route.request().url()).pathname }));
  await page.goto('http://127.0.0.1:8779/?demo=static', { waitUntil: 'networkidle' });
  await page.addStyleTag({ url: '/src/styles/site.css' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${OUT}/site-hero.png` });
  await page.addStyleTag({ content: '.site-header { position: static !important; }' });
  const card = page.locator('#signup');
  await card.scrollIntoViewIfNeeded();
  await card.screenshot({ path: `${OUT}/site-signup-empty.png` });
  await page.fill('#su-name', 'יואב');
  await page.fill('#su-phone', '050-0000000');
  await card.screenshot({ path: `${OUT}/site-signup-filled.png` });
  await page.click('#signup [type="submit"]');
  await page.waitForSelector('[data-form-done]:not([hidden])');
  await page.waitForTimeout(300);
  await card.screenshot({ path: `${OUT}/site-signup-done.png` });
  console.log('site captures written to', OUT);
} finally {
  await browser.close();
  server.kill();
}
