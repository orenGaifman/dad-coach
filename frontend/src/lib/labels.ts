/** Admin labels for product enums. */
export const FATHER_STATUS: Record<string, string> = {
  NOT_STARTED: 'לא התחיל', ONBOARDING: 'בהצטרפות', ACTIVE: 'פעיל', PAUSED: 'מושבת',
  CHURNED: 'נטש', REACTIVATED: 'חזר', DELETED: 'נמחק',
}

export const PHASE_LABEL: Record<string, string> = {
  UPCOMING: 'מתוכנן', IN_PROGRESS: 'עכשיו', AWAITING_CONFIRMATION: 'מחכה לאישור', COMPLETED: 'היה', CANCELLED: 'בוטל', MISSED: 'לא יצא',
}

export const DELIVERY_KIND: Record<string, string> = { SCHEDULED: 'הודעת מאמן מתוזמנת', LOGIN_LINK: 'כפתור כניסה' }

/** Why a WhatsApp message did not go out, in words. */
export function reasonLabel(reason: string | null | undefined): string {
  if (!reason) return '—'
  if (reason.startsWith('SESSION_CLOSED')) return 'מחוץ לחלון 24 השעות, בלי תבנית מאושרת'
  if (reason.startsWith('TEMPLATE_UNAVAILABLE')) return 'אין תבנית מאושרת (NO_APPROVED_TEMPLATE)'
  if (reason.startsWith('ENDPOINT_NOT_FOUND')) return 'אין לאבא ערוץ וואטסאפ רשום'
  if (reason.startsWith('WHATSAPP_NOT_CONFIGURED')) return 'וואטסאפ לא מוגדר בסביבה הזאת'
  // D-042: what the shared number's gateway reported about a message it held
  if (reason.startsWith('GATEWAY_HELD_EXPIRED')) return 'לא נשלח — חיכה יותר מדי זמן עד שהאב חזר'
  if (reason.startsWith('GATEWAY_UNKNOWN')) return 'לא ידוע אם הגיע — אין תשובה מוואטסאפ (לא נשלח שוב)'
  if (reason.startsWith('GATEWAY_')) return `וואטסאפ דחה את ההודעה שחיכתה (${reason.substring('GATEWAY_'.length)})`
  return reason
}

export const GOAL_STATUS: Record<string, string> = { ACTIVE: 'פעיל', COMPLETED: 'הושלם', MISSED: 'לא הושג', CANCELLED: 'בוטל' }

export const DELIVERY_STATUS: Record<string, string> = {
  DELIVERED: 'נמסר', FAILED: 'נכשל', SKIPPED: 'דולג', PENDING: 'ממתין', ISSUED: 'הונפק',
  // D-038: ACCEPTED = Meta took it (no delivery receipt yet); SENT / READ come from Meta's receipts
  ACCEPTED: 'נשלח', SENT: 'נשלח', READ: 'נקרא',
  // the shared number's gateway keeps it while the father talks to another product (sent when he is back)
  HELD: 'ממתין — האב בשיחה עם מוצר אחר',
  // D-042: the gateway sent it, but whether it reached WhatsApp is not known (never sent again)
  UNKNOWN: 'לא ידוע אם הגיע',
}

export const CHILD_STATUS: Record<string, string> = { ACTIVE: 'פעיל', ARCHIVED: 'בארכיון', INACTIVE: 'לא פעיל' }
