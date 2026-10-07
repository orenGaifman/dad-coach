import { execFileSync } from 'node:child_process'
import { expect, type Page } from '@playwright/test'

export const BACKEND = process.env.E2E_BACKEND_URL ?? 'http://localhost:8491'
export const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:5390'
const OPS_KEY = process.env.E2E_OPS_KEY ?? 'local-ops-key-0123456789abcdef'
const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? 'wsb-dc-postgres'
export const ADMIN_PHONE = process.env.E2E_ADMIN_PHONE ?? '+19990009001'
export const ADMIN_NAME = 'אורן'

export function testPhone(): string {
  return `+1999${Math.floor(1_000_000 + Math.random() * 8_999_999)}`
}

/** SQL against the local database (seeding only; every check goes through the UI or the API). */
export function sql(statement: string): string {
  return execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'psql', '-U', 'dadcoach', '-d', 'dadcoach', '-v', 'ON_ERROR_STOP=1', '-tAq'],
    { input: statement, encoding: 'utf8' }).trim()
}

const q = (s: string) => `'${s.replace(/'/g, "''")}'`

export async function ops<T>(path: string, body: unknown): Promise<T> {
  const r = await fetch(BACKEND + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-API-Key': OPS_KEY }, body: JSON.stringify(body),
  })
  if (!r.ok) throw new Error(`${path} -> ${r.status} ${await r.text()}`)
  return r.json() as Promise<T>
}

export async function ensureAdmin() {
  await ops('/api/ops/bootstrap-admin', { phone: ADMIN_PHONE, name: ADMIN_NAME })
}

/** Signs the page in through a real one-time login link (issued by the ops API, opened like the WhatsApp link). */
export async function signIn(page: Page, phone: string) {
  const { loginUrl } = await ops<{ loginUrl: string }>('/api/ops/login-links', { phone })
  const u = new URL(loginUrl)
  await page.goto(BASE + u.pathname + u.search + u.hash)
  await expect(page).not.toHaveURL(/\/auth\/consume/, { timeout: 15_000 })
}

/** The week in Israel: its Sunday 00:00 and next Sunday, plus now - for sessions that land inside this week. */
function israelWeek() {
  const now = new Date()
  const local = new Date(now.toLocaleString('en-US', { timeZone: 'Asia/Jerusalem' }))
  const offsetMs = local.getTime() - now.getTime()
  const startLocal = new Date(local)
  startLocal.setHours(0, 0, 0, 0)
  startLocal.setDate(startLocal.getDate() - startLocal.getDay())
  const start = new Date(startLocal.getTime() - offsetMs)
  const end = new Date(start.getTime() + 7 * 86_400_000)
  const date = `${startLocal.getFullYear()}-${String(startLocal.getMonth() + 1).padStart(2, '0')}-${String(startLocal.getDate()).padStart(2, '0')}`
  return { now, start, end, date }
}

const hour = 3_600_000
const iso = (d: Date) => d.toISOString()
const floorHour = (t: number) => new Date(Math.floor(t / hour) * hour)

export interface Yoav { id: number; phone: string; noa: number; itai: number }

/**
 * The demo father for the screenshots (ad + training videos): יואב, with נועה (7) and איתי (4); a 3-hour goal, two
 * sessions this week - one done - and a yellow belt from earlier weeks. Times relative to now, inside this week.
 */
export function seedYoav(): Yoav {
  const phone = testPhone()
  const w = israelWeek()
  const id = Number(sql(`INSERT INTO father (phone, display_name, status, timezone, current_belt, total_quality_times_completed,
      quality_time_streak, current_streak_weeks, longest_streak_weeks, metadata)
    VALUES (${q(phone)}, 'יואב', 'ACTIVE', 'Asia/Jerusalem', 'YELLOW', 4, 4, 2, 2, '{"e2e": true}') RETURNING id;`))
  const noa = Number(sql(`INSERT INTO child (father_id, name, birth_date, gender) VALUES (${id}, 'נועה', current_date - interval '7 years 2 months', 'girl') RETURNING id;`))
  const itai = Number(sql(`INSERT INTO child (father_id, name, birth_date, gender) VALUES (${id}, 'איתי', current_date - interval '4 years 5 months', 'boy') RETURNING id;`))
  const sinceStart = w.now.getTime() - w.start.getTime()
  const untilEnd = w.end.getTime() - w.now.getTime()
  // done: yesterday at this hour if that is still this week, else as early in the week as possible
  const doneStart = floorHour(Math.max(w.start.getTime() + hour, w.now.getTime() - 26 * hour, w.now.getTime() - sinceStart + hour))
  const safeDone = doneStart.getTime() + hour < w.now.getTime() ? doneStart : floorHour(w.now.getTime() - 2 * hour)
  // ahead: tomorrow at this hour if still this week, else later today
  const aheadStart = floorHour(Math.min(w.now.getTime() + 26 * hour, w.end.getTime() - 2 * hour))
  const safeAhead = aheadStart.getTime() > w.now.getTime() + hour ? aheadStart : floorHour(w.now.getTime() + 2 * hour)
  if (untilEnd < 3 * hour || sinceStart < 3 * hour) console.warn('seedYoav: close to the week boundary; sessions may fall outside it')
  const prev1 = new Date(w.start.getTime() - 7 * 86_400_000 + 2 * 86_400_000 + 17 * hour)
  const prev2 = new Date(w.start.getTime() - 14 * 86_400_000 + 3 * 86_400_000 + 17 * hour)
  sql(`
    INSERT INTO quality_time (father_id, child_id, scheduled_start, scheduled_end, status, completion_notes, completed_at) VALUES
      (${id}, ${noa}, ${q(iso(safeDone))}, ${q(iso(new Date(safeDone.getTime() + hour)))}, 'COMPLETED', 'בנינו מגדל לגו ענק ודיברנו על החברים בכיתה', now()),
      (${id}, ${itai}, ${q(iso(safeAhead))}, ${q(iso(new Date(safeAhead.getTime() + hour)))}, 'SCHEDULED', NULL, NULL),
      (${id}, ${itai}, ${q(iso(prev1))}, ${q(iso(new Date(prev1.getTime() + hour)))}, 'COMPLETED', 'פארק וגלידה', now()),
      (${id}, ${noa}, ${q(iso(new Date(prev1.getTime() + 2 * 86_400_000)))}, ${q(iso(new Date(prev1.getTime() + 2 * 86_400_000 + 2 * hour)))}, 'COMPLETED', NULL, now()),
      (${id}, ${noa}, ${q(iso(prev2))}, ${q(iso(new Date(prev2.getTime() + 2 * hour)))}, 'COMPLETED', 'אפינו עוגיות', now());
    INSERT INTO weekly_goal (father_id, week_start_date, target_hours, actual_minutes, starting_belt, status, completed_count) VALUES
      (${id}, ${q(w.date)}, 3, 60, 'YELLOW', 'ACTIVE', 1),
      (${id}, (${q(w.date)}::date - 7), 3, 180, 'WHITE', 'COMPLETED', 2),
      (${id}, (${q(w.date)}::date - 14), 2, 120, 'WHITE', 'COMPLETED', 1);`)
  return { id, phone, noa, itai }
}

/** A father with one session that ended an hour ago and waits for his "it happened". */
export function seedAwaitingFather(name: string): { id: number; phone: string; session: string } {
  const phone = testPhone()
  const id = Number(sql(`INSERT INTO father (phone, display_name, status, timezone, metadata) VALUES (${q(phone)}, ${q(name)}, 'ACTIVE', 'Asia/Jerusalem', '{"e2e": true}') RETURNING id;`))
  const child = Number(sql(`INSERT INTO child (father_id, name, birth_date) VALUES (${id}, 'מיקה', current_date - interval '6 years') RETURNING id;`))
  const start = floorHour(Date.now() - 2 * hour)
  const session = sql(`INSERT INTO quality_time (father_id, child_id, scheduled_start, scheduled_end, status)
    VALUES (${id}, ${child}, ${q(iso(start))}, ${q(iso(new Date(start.getTime() + 45 * 60_000)))}, 'SCHEDULED') RETURNING id;`)
  return { id, phone, session }
}

/** A father who just finished onboarding in WhatsApp: one child, no goal, no sessions. */
export function seedNewFather(name: string, childName: string): { id: number; phone: string } {
  const phone = testPhone()
  const id = Number(sql(`INSERT INTO father (phone, display_name, status, timezone, metadata) VALUES (${q(phone)}, ${q(name)}, 'ACTIVE', 'Asia/Jerusalem', '{"e2e": true}') RETURNING id;`))
  sql(`INSERT INTO child (father_id, name, birth_date) VALUES (${id}, ${q(childName)}, current_date - interval '5 years');`)
  return { id, phone }
}
