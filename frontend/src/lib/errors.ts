import { ApiError } from './api'

/** Hebrew text per backend error code (masculine singular). The English `message` never reaches the screen. */
const MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'חלק מהפרטים חסרים או לא תקינים. כדאי לבדוק ולנסות שוב.',
  INVALID_NAME: 'צריך שם (עד 100 תווים).',
  INVALID_AGE: 'הגיל צריך להיות בין 0 ל-25.',
  DUPLICATE_CHILD: 'כבר יש ילד בשם הזה.',
  TOO_MANY_CHILDREN: 'אפשר להוסיף עד 8 ילדים.',
  INVALID_TIMEZONE: 'אזור הזמן הזה לא מוכר.',
  SESSION_NOT_CONFIRMABLE: 'אפשר לאשר מפגש רק אחרי שהתחיל.',
  SESSION_NOT_CANCELLABLE: 'המפגש הזה כבר נסגר, אז אי אפשר לבטל אותו.',
  CALENDAR_NOT_CONFIGURED: 'החיבור ליומן גוגל עוד לא זמין.',
  CONFIRMATION_MISMATCH: 'המילה שהוקלדה לא תואמת. כדאי לבדוק ולנסות שוב.',
  DEACTIVATE_FIRST: 'צריך להשבית קודם, ורק אז למחוק.',
  NOT_DEACTIVATED: 'האבא הזה לא מושבת.',
  ALREADY_DELETED: 'האבא הזה כבר בתהליך מחיקה.',
  NOTE_TOO_LONG: 'ההערה ארוכה מדי (עד 500 תווים).',
  NOT_FOUND: 'לא מצאתי את מה שחיפשת, או שאין לך גישה אליו.',
  NOT_A_FATHER: 'האזור הזה מיועד לאבות שמשתמשים בדאד קואץ׳.',
  NOT_STAFF: 'האזור הזה מיועד לצוות דאד קואץ׳.',
  UNAUTHENTICATED: 'צריך להתחבר מחדש.',
  INVALID_LOGIN_LINK: 'הכפתור הזה כבר לא בתוקף. כתוב "דשבורד" למאמן בוואטסאפ ותקבל כפתור חדש.',
  CSRF_REJECTED: 'הפעולה נחסמה. כדאי לרענן את הדף ולנסות שוב.',
  FORBIDDEN: 'הפעולה נחסמה. כדאי לרענן את הדף ולנסות שוב.',
  NETWORK: 'אין חיבור לשרת. כדאי לבדוק את החיבור לאינטרנט ולנסות שוב.',
}

const FALLBACK = 'משהו השתבש אצלנו. אפשר לנסות שוב, ואם זה חוזר - לפנות אלינו עם קוד הבירור.'

export function errorText(error: unknown): string {
  if (error instanceof ApiError && MESSAGES[error.code]) return MESSAGES[error.code]
  return FALLBACK
}

/** One line (toasts): the text, plus the support code when it is the generic fallback. */
export function errorLine(error: unknown): string {
  const cid = correlationOf(error)
  return cid ? `${errorText(error)} (קוד לבירור: ${cid})` : errorText(error)
}

/** The correlation id, only when the message is the generic fallback (known codes need no support id). */
export function correlationOf(error: unknown): string | undefined {
  if (error instanceof ApiError && !MESSAGES[error.code]) return error.correlationId
  return undefined
}
