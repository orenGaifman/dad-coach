import { expect, test } from '@playwright/test'
import { ADMIN_PHONE, BACKEND, ensureAdmin, seedYoav, signIn, testPhone } from './fixtures'

test.beforeAll(ensureAdmin)

test('the login page sends him to the coach for a button; the phone form is the second way', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'כניסה לדף שלך' })).toBeVisible()
  await expect(page.getByRole('link', { name: /לכתוב "דשבורד" למאמן/ })).toHaveAttribute('href', /wa\.me\/.*text=/)
  await expect(page.getByText('10 דקות')).toHaveCount(0)
  await page.getByRole('button', { name: 'לשלוח את הכפתור לפי מספר טלפון' }).click()
  await page.getByLabel('מספר טלפון').fill(testPhone())
  await page.getByRole('button', { name: 'שלחו לי כפתור כניסה' }).click()
  await expect(page.getByRole('heading', { name: 'הכפתור בדרך אליך' })).toBeVisible()
  expect(page.url()).not.toContain('phone')
})

test('a login link signs the father in to his week; logout signs him out', async ({ page }) => {
  const yoav = seedYoav()
  await signIn(page, yoav.phone)
  await expect(page).toHaveURL(/\/home$/)
  await expect(page.getByRole('heading', { name: 'שלום, יואב' })).toBeVisible()
  await page.getByRole('button', { name: /תפריט חשבון/ }).click()
  await page.getByRole('menuitem', { name: 'יציאה', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('יצאת מהחשבון.')).toBeVisible()
  await page.goto('/home')
  await expect(page).toHaveURL(/\/login\?next=%2Fhome/)
})

test('the button keeps working: the same link signs in again after the browser forgets the session', async ({ page }) => {
  const r = await fetch(BACKEND + '/api/ops/login-links', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-API-Key': process.env.E2E_OPS_KEY ?? 'local-ops-key-0123456789abcdef' },
    body: JSON.stringify({ phone: ADMIN_PHONE }),
  })
  const { loginUrl } = await r.json()
  const u = new URL(loginUrl)
  await page.goto(u.pathname + u.hash)
  await expect(page).toHaveURL(/\/admin$/)
  await page.context().clearCookies()
  await page.goto(u.pathname + u.hash)
  await expect(page).toHaveURL(/\/admin$/)
})

test('a button that no longer works: signed in here -> his page; otherwise how to get a new one', async ({ page }) => {
  await page.goto('/auth/consume#token=not-a-real-token')
  await expect(page.getByRole('heading', { name: 'הכפתור הזה כבר לא פעיל' })).toBeVisible()
  const yoav = seedYoav()
  await signIn(page, yoav.phone)
  await page.goto('/auth/consume#token=not-a-real-token')
  await expect(page).toHaveURL(/\/home$/)
})
