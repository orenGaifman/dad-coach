// Hebrew formatting helpers - pure functions (unit tested in format.test.ts).

const WEEKDAYS: Record<string, string> = {
  SUNDAY: 'ראשון', MONDAY: 'שני', TUESDAY: 'שלישי', WEDNESDAY: 'רביעי',
  THURSDAY: 'חמישי', FRIDAY: 'שישי', SATURDAY: 'שבת',
}

export function weekdayName(weekday: string): string {
  return WEEKDAYS[weekday] ?? weekday
}

/** "שעה", "שעתיים", "3 שעות", "45 דק׳", "שעה ו-15 דק׳". */
export function duration(minutes: number): string {
  const m = Math.max(0, Math.round(minutes))
  const h = Math.floor(m / 60)
  const rest = m % 60
  const hours = h === 0 ? '' : h === 1 ? 'שעה' : h === 2 ? 'שעתיים' : `${h} שעות`
  if (rest === 0) return hours || '0 דק׳'
  if (!hours) return `${rest} דק׳`
  return `${hours} ו-${rest} דק׳`
}

/** "8.10" from "2026-10-08". */
export function dayMonth(isoDate: string): string {
  const [, mm, dd] = isoDate.split('-')
  return `${Number(dd)}.${Number(mm)}`
}

function daysBetween(fromIso: string, toIso: string): number {
  const a = Date.UTC(+fromIso.slice(0, 4), +fromIso.slice(5, 7) - 1, +fromIso.slice(8, 10))
  const b = Date.UTC(+toIso.slice(0, 4), +toIso.slice(5, 7) - 1, +toIso.slice(8, 10))
  return Math.round((b - a) / 86_400_000)
}

/** "היום", "מחר", "אתמול", or "יום שלישי, 8.10" - relative to the father's own today. */
export function dayLabel(localDate: string, weekday: string, today: string): string {
  const d = daysBetween(today, localDate)
  if (d === 0) return 'היום'
  if (d === 1) return 'מחר'
  if (d === -1) return 'אתמול'
  return `יום ${weekdayName(weekday)}, ${dayMonth(localDate)}`
}

/** "4.10–10.10" */
export function weekRange(start: string, end: string): string {
  return `${dayMonth(start)}–${dayMonth(end)}`
}

/** "נשאר יום אחד" / "נשארו 4 ימים" / "השבוע נגמר היום". */
export function daysLeft(n: number): string {
  if (n <= 1) return 'השבוע נגמר היום'
  if (n === 2) return 'נשארו יומיים'
  return `נשארו ${n} ימים`
}

/** Israeli numbers as people write them ("050-123-4567"); anything else as is. */
export function displayPhone(e164: string): string {
  const m = /^\+972(\d{2})(\d{3})(\d{4})$/.exec(e164)
  return m ? `0${m[1]}-${m[2]}-${m[3]}` : e164
}

export function ageLabel(age: number): string {
  if (age === 0) return 'פחות משנה'
  if (age === 1) return 'בן שנה'
  if (age === 2) return 'בן שנתיים'
  return `בן ${age}`
}

/** A timestamp for the admin: "7.10.2026, 14:05" in the browser's zone. */
export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('he-IL', { day: 'numeric', month: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function percent(part: number, whole: number | null | undefined): number {
  if (!whole || whole <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((100 * part) / whole)))
}
