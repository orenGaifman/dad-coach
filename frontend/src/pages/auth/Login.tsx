import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useSearchParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { displayPhone } from '../../lib/format'
import { homeFor, safeNext, useMe } from '../../lib/session'
import { coachLink, DASHBOARD_WORD } from '../../lib/whatsapp'
import { Icon, Logomark } from '../../shared/Icon'
import { FormError } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Auth.module.css'

interface SignInInfo { whatsappNumber: string | null }
interface RequestLinkAnswer { status: string; whatsappNumber: string | null }

/**
 * The way in (D-027): the coach sends a personal button on WhatsApp that always works - write him "דשבורד". The
 * phone form is only the second way: it sends the same button. The phone is never taken from the address (no
 * personal data in URLs), and the answer is the same whether or not the number is registered. Someone already signed
 * in on this device goes straight to his page.
 */
export function Login() {
  const [params] = useSearchParams()
  const location = useLocation()
  const loggedOut = (location.state as { loggedOut?: string } | null)?.loggedOut
  const me = useMe()
  const info = useQuery({ queryKey: ['sign-in-info'], queryFn: () => api<SignInInfo>('/auth/sign-in-info'), staleTime: Infinity })
  const [showPhone, setShowPhone] = useState(false)
  const [phone, setPhone] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [invalid, setInvalid] = useState(false)

  useEffect(() => { document.title = 'כניסה · דאד קואץ׳' }, [])

  if (me.data && !loggedOut) {
    const next = safeNext(params.get('next'))
    return <Navigate to={next ?? homeFor(me.data)} replace />
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (phone.replace(/\D/g, '').length < 9) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setBusy(true)
    setError(null)
    try {
      await api<RequestLinkAnswer>('/auth/request-link', {
        method: 'POST', body: { phone: phone.trim(), next: safeNext(params.get('next')) ?? undefined },
      })
      setSent(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const number = info.data?.whatsappNumber ?? null
  const wa = coachLink(number, DASHBOARD_WORD)

  return (
    <main className={styles.screen}>
      <div className={styles.card}>
        <div className={styles.brand}>
          <Logomark size={52} />
          <span>דאד קואץ׳</span>
        </div>
        {sent ? (
          <div className={styles.sent} role="status">
            <span className={styles.sentIcon}><Icon name="whatsapp" size={26} /></span>
            <h1 className={styles.title}>הכפתור בדרך אליך</h1>
            <p className={styles.lead}>אם המספר רשום אצלנו, שלחנו לך בוואטסאפ כפתור כניסה אישי. הוא ממשיך לעבוד, אז אפשר לחזור אליו בכל פעם.</p>
            <div className={cx(ui.note)}>
              <Icon name="info" size={18} />
              <span>
                לא הגיע? כתוב למאמן "{DASHBOARD_WORD}" בוואטסאפ{number ? <> (<span className="ltr">{displayPhone(number)}</span>)</> : null} והוא ישלח אותו.
              </span>
            </div>
            {wa && <a className={cx(ui.btn, ui.whatsapp, ui.block)} href={wa} target="_blank" rel="noreferrer">
              <Icon name="whatsapp" size={20} /> לכתוב למאמן בוואטסאפ
            </a>}
            <button type="button" className={ui.linkBtn} onClick={() => { setSent(false); setPhone('') }}>
              לשלוח למספר אחר
            </button>
          </div>
        ) : (
          <div className={styles.form}>
            <h1 className={styles.title}>כניסה לדף שלך</h1>
            <p className={styles.lead}>
              בלי סיסמה ובלי קודים: כתוב למאמן <strong>"{DASHBOARD_WORD}"</strong> בוואטסאפ, ותקבל כפתור כניסה אישי שעובד תמיד.
            </p>
            {loggedOut && (
              <p className={cx(ui.note)}>
                <Icon name="check" size={18} />
                {loggedOut === 'all' ? 'יצאת מהחשבון בכל המכשירים, וכפתורי הכניסה הישנים בוטלו.' : loggedOut === 'deleted' ? 'בקשת המחיקה התקבלה. הנתונים שלך יימחקו.' : 'יצאת מהחשבון.'}
              </p>
            )}
            {wa ? (
              <a className={cx(ui.btn, ui.whatsapp, ui.block)} href={wa} target="_blank" rel="noreferrer">
                <Icon name="whatsapp" size={20} /> לכתוב "{DASHBOARD_WORD}" למאמן
              </a>
            ) : (
              <p className={cx(ui.note)}>
                <Icon name="whatsapp" size={18} />
                <span>פתח את השיחה עם המאמן בוואטסאפ וכתוב "{DASHBOARD_WORD}".</span>
              </p>
            )}
            {showPhone ? (
              <form onSubmit={submit} noValidate className={styles.phoneForm}>
                <p className={ui.muted}>או: נשלח את הכפתור לוואטסאפ של המספר שאיתו אתה מדבר עם המאמן.</p>
                <div className={ui.field}>
                  <label className={ui.label} htmlFor="phone">מספר טלפון</label>
                  <input id="phone" className={ui.input} type="tel" inputMode="tel" autoComplete="tel" dir="ltr"
                         placeholder="050-1234567" value={phone} onChange={(e) => setPhone(e.target.value)}
                         aria-invalid={invalid} aria-describedby={invalid ? 'phone-err' : undefined} required autoFocus />
                  {invalid && <span id="phone-err" className={ui.fieldError}>צריך מספר טלפון מלא.</span>}
                </div>
                <FormError error={error} />
                <button type="submit" className={cx(ui.btn, ui.ghost, ui.block)} disabled={busy}>
                  {busy ? 'שולח…' : 'שלחו לי כפתור כניסה'}
                </button>
              </form>
            ) : (
              <button type="button" className={cx(ui.linkBtn, styles.secondaryWay)} onClick={() => setShowPhone(true)}>
                לשלוח את הכפתור לפי מספר טלפון
              </button>
            )}
          </div>
        )}
      </div>
      <p className={styles.foot}>
        דאד קואץ׳ · זמן אמיתי עם הילדים, כל שבוע · <Link to="/privacy">פרטיות</Link> · <Link to="/terms">תנאים</Link>
      </p>
    </main>
  )
}
