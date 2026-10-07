import { expect, test } from '@playwright/test'
import { ADMIN_PHONE, ensureAdmin, seedYoav, signIn, testPhone } from './fixtures'

test.beforeAll(ensureAdmin)

test('the login page asks for a phone and answers the same for anyone', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'כניסה ללוח שלך' })).toBeVisible()
  await page.getByLabel('מספר טלפון').fill(testPhone())
  await page.getByRole('button', { name: 'שלחו לי קישור כניסה' }).click()
  await expect(page.getByRole('heading', { name: 'הקישור בדרך אליך' })).toBeVisible()
  await expect(page.getByText('לא הגיע? כתוב לנו בוואטסאפ')).toBeVisible()
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

test('a used link does not work twice', async ({ page, request }) => {
  const r = await request.post('http://localhost:8491/api/ops/login-links', {
    headers: { 'X-API-Key': process.env.E2E_OPS_KEY ?? 'local-ops-key-0123456789abcdef' }, data: { phone: ADMIN_PHONE },
  })
  const { loginUrl } = await r.json()
  const u = new URL(loginUrl)
  await page.goto(u.pathname + u.hash)
  await expect(page).toHaveURL(/\/admin$/)
  await page.context().clearCookies()
  await page.goto(u.pathname + u.hash)
  await expect(page.getByRole('heading', { name: 'לא הצלחתי להכניס אותך' })).toBeVisible()
})
