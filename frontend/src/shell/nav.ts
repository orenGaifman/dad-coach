export interface NavItem {
  to: string
  label: string
  /** Shorter label for the mobile tab bar. */
  short?: string
  icon: string
  end?: boolean
  /** On mobile: a tab of its own, or inside "עוד". */
  primary?: boolean
  /** Shown only with this capability (from /api/me). */
  needs?: string
}

/** One nav model per area; the sidebar and the bottom tabs both render from it. */
export const FATHER_NAV: NavItem[] = [
  { to: '/home', label: 'השבוע שלי', short: 'בית', icon: 'home', end: true, primary: true },
  { to: '/sessions', label: 'מפגשים', icon: 'calendar', primary: true },
  { to: '/children', label: 'ילדים', icon: 'users', primary: true },
  { to: '/progress', label: 'התקדמות', icon: 'medal', primary: true },
  { to: '/settings', label: 'הגדרות', icon: 'settings', primary: true },
  { to: '/training', label: 'הדרכה', icon: 'play', needs: 'training' },
]

export const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'סקירה', icon: 'grid', end: true, primary: true },
  { to: '/admin/fathers', label: 'אבות', icon: 'users', primary: true },
  { to: '/admin/undelivered', label: 'הודעות שלא נמסרו', short: 'לא נמסרו', icon: 'alert', primary: true },
  { to: '/admin/templates', label: 'תבניות וואטסאפ', short: 'תבניות', icon: 'whatsapp' },
  { to: '/admin/integrations', label: 'אינטגרציות', icon: 'link', primary: true },
  { to: '/admin/deletions', label: 'השבתה ומחיקה', icon: 'trash' },
  { to: '/admin/training', label: 'הדרכה', icon: 'play' },
]

export function visibleNav(nav: NavItem[], capabilities: string[] | undefined): NavItem[] {
  return nav.filter((item) => !item.needs || !!capabilities?.includes(item.needs))
}

export function isActive(item: NavItem, pathname: string): boolean {
  return item.end ? pathname === item.to || pathname === `${item.to}/` : pathname === item.to || pathname.startsWith(`${item.to}/`)
}
