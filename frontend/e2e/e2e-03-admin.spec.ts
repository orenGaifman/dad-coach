import { expect, test } from '@playwright/test'
import { ADMIN_PHONE, ensureAdmin, seedYoav, signIn, sql } from './fixtures'

test.beforeAll(ensureAdmin)

test('a father cannot open the admin area', async ({ page }) => {
  const yoav = seedYoav()
  await signIn(page, yoav.phone)
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/home$/)
})

test('admin: find a father, see his detail, view his home read-only', async ({ page }) => {
  const yoav = seedYoav()
  await signIn(page, ADMIN_PHONE)
  await expect(page.getByRole('heading', { name: 'סקירה' })).toBeVisible()
  await page.getByRole('link', { name: 'אבות' }).first().click()
  await page.getByLabel('חיפוש').fill(yoav.phone.slice(-7))
  await page.getByRole('button', { name: 'חיפוש' }).click()
  await expect(page).toHaveURL(/q=/)
  await expect(page.getByRole('link', { name: 'יואב' })).toHaveCount(1)
  await page.getByRole('link', { name: 'יואב' }).click()
  await expect(page.getByRole('heading', { name: 'יואב', level: 1 })).toBeVisible()
  await expect(page.getByRole('listitem').filter({ hasText: 'נועה' })).toBeVisible()
  await page.getByRole('button', { name: 'טעינה מהפלטפורמה' }).click()
  await expect(page.getByText('לא מוגדר: חסר WORKFLOW_PLATFORM_ADMIN_API_KEY.')).toBeVisible()
  await page.getByRole('link', { name: 'כך הוא רואה את הלוח' }).click()
  await expect(page.getByText('תצוגה בלבד, אי אפשר לשנות מכאן.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'שלום, יואב' })).toBeVisible()
  await expect(page.getByText('מתוך 3 שעות')).toBeVisible()
  await expect(page.getByRole('button', { name: 'דבר עם המאמן בוואטסאפ' })).toBeDisabled()
})

test('admin: deactivate, then a typed permanent delete', async ({ page }) => {
  const yoav = seedYoav()
  await signIn(page, ADMIN_PHONE)
  await page.goto(`/admin/fathers/${yoav.id}`)
  await page.getByRole('button', { name: 'מחיקה לצמיתות' }).isDisabled()
  await page.getByRole('button', { name: 'השבתה' }).click()
  await expect(page.getByText('הושבת.')).toBeVisible()
  await page.getByRole('button', { name: 'מחיקה לצמיתות' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/כדי לאשר/).fill('יואב')
  await dialog.getByRole('button', { name: 'למחוק' }).click()
  await expect(page).toHaveURL(/\/admin\/deletions$/)
  expect(sql(`SELECT count(*) FROM father WHERE id = ${yoav.id};`)).toBe('0')
})
