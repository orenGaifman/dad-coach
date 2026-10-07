/** Admin labels for product enums. */
export const FATHER_STATUS: Record<string, string> = {
  NOT_STARTED: 'לא התחיל', ONBOARDING: 'בהצטרפות', ACTIVE: 'פעיל', PAUSED: 'מושבת',
  CHURNED: 'נטש', REACTIVATED: 'חזר', DELETED: 'נמחק',
}

export const PHASE_LABEL: Record<string, string> = {
  UPCOMING: 'מתוכנן', IN_PROGRESS: 'עכשיו', AWAITING_CONFIRMATION: 'מחכה לאישור', COMPLETED: 'היה', CANCELLED: 'בוטל', MISSED: 'לא יצא',
}

export const DELIVERY_KIND: Record<string, string> = { SCHEDULED: 'הודעת מאמן מתוזמנת', LOGIN_LINK: 'קישור כניסה' }

/** Why a WhatsApp message did not go out, in words. */
export function reasonLabel(reason: string | null | undefined): string {
  if (!reason) return '—'
  if (reason.startsWith('SESSION_CLOSED')) return 'מחוץ לחלון 24 השעות, בלי תבנית מאושרת'
  if (reason.startsWith('TEMPLATE_UNAVAILABLE')) return 'אין תבנית מאושרת (NO_APPROVED_TEMPLATE)'
  if (reason.startsWith('ENDPOINT_NOT_FOUND')) return 'אין לאבא ערוץ וואטסאפ רשום'
  if (reason.startsWith('WHATSAPP_NOT_CONFIGURED')) return 'וואטסאפ לא מוגדר בסביבה הזאת'
  return reason
}

export const GOAL_STATUS: Record<string, string> = { ACTIVE: 'פעיל', COMPLETED: 'הושלם', MISSED: 'לא הושג', CANCELLED: 'בוטל' }

export const DELIVERY_STATUS: Record<string, string> = {
  DELIVERED: 'נמסר', FAILED: 'נכשל', SKIPPED: 'דולג', PENDING: 'ממתין', HELD: 'ממתין לשעה מתאימה', ISSUED: 'הונפק',
}

export const CHILD_STATUS: Record<string, string> = { ACTIVE: 'פעיל', ARCHIVED: 'בארכיון', INACTIVE: 'לא פעיל' }
