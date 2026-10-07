// The films and where they live (served from marketing/ by build.sh on 127.0.0.1:8788).
export const BASE = 'http://127.0.0.1:8788';
export const url = (v) => (v === 'ad' || v === 'ad45' ? `${BASE}/ad/film/index.html${v === 'ad45' ? '?f=45' : ''}` : `${BASE}/training/film/index.html?v=${v}`);
export const size = (v) => (v === 'ad45' ? { width: 1080, height: 1350 } : { width: 1080, height: 1920 });
export async function open(browser, v, dpr = 1) {
  const page = await browser.newPage({ viewport: size(v), deviceScaleFactor: dpr });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(url(v));
  await page.waitForFunction(() => window.ready && window.renderAt && window.TIMELINE, null, { timeout: 60000 });
  await page.evaluate(() => window.ready);
  return { page, errors };
}
