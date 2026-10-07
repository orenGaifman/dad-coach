import type { Belt } from './types'

const NAMES: Record<Belt, string> = {
  WHITE: 'לבנה', YELLOW: 'צהובה', ORANGE: 'כתומה', GREEN: 'ירוקה', BLUE: 'כחולה', BROWN: 'חומה', BLACK: 'שחורה',
}

/** "חגורה צהובה". */
export function beltName(belt: Belt): string {
  return `חגורה ${NAMES[belt] ?? belt}`
}

/** The same pictures WhatsApp sends on a promotion (served at <WEB_BASE_URL>/belts/<belt>-belt.webp). */
export function beltImage(belt: Belt): string {
  return `/belts/${belt.toLowerCase()}-belt.webp`
}

/** "עוד 7 מפגשים לחגורה כתומה" / "עוד מפגש אחד ל..." / the top. */
export function toNextBelt(next: Belt | null, sessions: number | null): string {
  if (!next || sessions == null) return 'הגעת לחגורה השחורה. כל הכבוד.'
  if (sessions <= 0) return `הבא בתור: ${beltName(next)}`
  if (sessions === 1) return `עוד מפגש אחד ל${beltName(next)}`
  return `עוד ${sessions} מפגשים ל${beltName(next)}`
}
