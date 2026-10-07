import { mkdirSync } from 'node:fs'
import { expect, test, type Browser, type Page } from '@playwright/test'
import { ADMIN_PHONE, ensureAdmin, seedAwaitingFather, seedNewFather, seedYoav, signIn } from './fixtures'

/**
 * Every screen, phone (390x844 @2x) and desktop (1440x900), with the demo data (יואב, נועה 7, איתי 4, a 3-hour goal,
 * one of two sessions done, yellow belt) - the pictures the ad and the training videos are cut from.
 * Saved under e2e/screenshots/{phone,desktop}/.
 */
const SIZES = [
  { name: 'phone', viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
  { name: 'desktop', viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, isMobile: false, hasTouch: false },
] as const

async function shoot(page: Page, dir: string, name: string) {
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  await expect(page.locator('[role="status"][aria-live="polite"] .visually-hidden')).toHaveCount(0, { timeout: 10_000 }).catch(() => {})
  await page.waitForTimeout(250)
  await page.screenshot({ path: `${dir}/${name}.png`, fullPage: true })
  if (dir.endsWith('phone') && await page.locator('[class^="_shell_"]').count() > 0) {
    // On a phone only the content scrolls (bottom tabs stay put); the long version lets the page flow instead.
    const style = await page.addStyleTag({ content: '[class^="_shell_"], [class^="_body_"] { block-size: auto !important; overflow: visible !important; }' })
    await page.screenshot({ path: `${dir}/${name}-full.png`, fullPage: true })
    await style.evaluate((el) => el.remove())
  }
}

async function context(browser: Browser, size: (typeof SIZES)[number]) {
  return browser.newContext({
    viewport: size.viewport, deviceScaleFactor: size.deviceScaleFactor, isMobile: size.isMobile, hasTouch: size.hasTouch,
    locale: 'he-IL', timezoneId: 'Asia/Jerusalem',
  })
}

test.beforeAll(ensureAdmin)

for (const size of SIZES) {
  test(`screenshots - ${size.name}`, async ({ browser }) => {
    test.setTimeout(180_000)
    const dir = `e2e/screenshots/${size.name}`
    mkdirSync(dir, { recursive: true })
    const yoav = seedYoav()
    const ctx = await context(browser, size)
    const page = await ctx.newPage()

    await page.goto('/login')
    await shoot(page, dir, '01-login')
    await page.getByLabel('מספר טלפון').fill('050-1234567')
    await page.getByRole('button', { name: 'שלחו לי קישור כניסה' }).click()
    await expect(page.getByRole('heading', { name: 'הקישור בדרך אליך' })).toBeVisible()
    await shoot(page, dir, '02-login-sent')

    await signIn(page, yoav.phone)
    await expect(page.getByRole('heading', { name: 'שלום, יואב' })).toBeVisible()
    await shoot(page, dir, '03-home')
    await page.goto('/sessions')
    await expect(page.getByRole('heading', { name: 'מפגשים', level: 1 })).toBeVisible()
    await shoot(page, dir, '04-sessions')
    await page.getByRole('radio', { name: /^היו/ }).check()
    await shoot(page, dir, '05-sessions-past')
    await page.goto('/children')
    await expect(page.getByText('איתי')).toBeVisible()
    await shoot(page, dir, '06-children')
    await page.goto('/progress')
    await expect(page.getByRole('heading', { name: 'הישגים' })).toBeVisible()
    await shoot(page, dir, '07-progress')
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: 'יומן Google' })).toBeVisible()
    await shoot(page, dir, '08-settings')
    await page.goto('/training')
    await shoot(page, dir, '09-training-soon')

    // a father with a session waiting for his "it happened"
    const dan = seedAwaitingFather('דן')
    await signIn(page, dan.phone)
    await expect(page.getByRole('heading', { name: 'מחכה לאישור שלך' })).toBeVisible()
    await shoot(page, dir, '10-home-awaiting')
    await page.getByRole('button', { name: 'היה!' }).click()
    await page.getByLabel('מה עשיתם? (לא חובה)').fill('קראנו ספר על דינוזאורים')
    await page.screenshot({ path: `${dir}/11-confirm-drawer.png` })
    await page.keyboard.press('Escape')

    // a brand-new father: the empty states tell him what to write
    await signIn(page, seedNewFather('עומר', 'שירה').phone)
    await expect(page.getByText('עוד לא קבעת יעד לשבוע')).toBeVisible()
    await shoot(page, dir, '12-home-new-father')

    await page.goto('/privacy')
    await shoot(page, dir, '13-privacy')
    await page.goto('/data-deletion')
    await shoot(page, dir, '14-data-deletion')

    await signIn(page, ADMIN_PHONE)
    await expect(page.getByRole('heading', { name: 'סקירה' })).toBeVisible()
    await shoot(page, dir, '20-admin-overview')
    await page.goto('/admin/fathers')
    await expect(page.getByRole('link', { name: 'יואב' }).first()).toBeVisible()
    await shoot(page, dir, '21-admin-fathers')
    await page.goto(`/admin/fathers/${yoav.id}`)
    await expect(page.getByRole('heading', { name: 'יואב', level: 1 })).toBeVisible()
    await shoot(page, dir, '22-admin-father')
    await page.goto(`/admin/fathers/${yoav.id}/home`)
    await expect(page.getByText('תצוגה בלבד, אי אפשר לשנות מכאן.')).toBeVisible()
    await shoot(page, dir, '23-admin-view-as')
    await page.goto('/admin/undelivered')
    await shoot(page, dir, '24-admin-undelivered')
    await page.goto('/admin/integrations')
    await shoot(page, dir, '25-admin-integrations')
    await page.goto('/admin/deletions')
    await shoot(page, dir, '26-admin-deletions')
    await page.goto('/admin/training')
    await shoot(page, dir, '27-admin-training')
    await ctx.close()
  })
}

