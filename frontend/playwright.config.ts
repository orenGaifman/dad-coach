import { defineConfig, devices } from '@playwright/test'

/**
 * Real end to end - no mocks - against a running backend (local profile, Postgres on :55490, port 8491) and the Vite
 * dev server (:5390, /api proxied to it). Start them first (docs/implementation/DEPLOYMENT.md "Dashboard - local"),
 * then `npx playwright test`. Fathers are seeded with SQL on +1999 numbers (marked e2e) and deleted afterwards
 * through the admin's deactivate -> delete path.
 */
export default defineConfig({
  testDir: './e2e',
  globalTeardown: './e2e/global-teardown.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5390',
    locale: 'he-IL',
    timezoneId: 'Asia/Jerusalem',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
})
