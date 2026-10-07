import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { api } from '../../lib/api'
import { displayPhone } from '../../lib/format'
import { safeNext } from '../../lib/session'
import { coachLink } from '../../lib/whatsapp'
import { Icon, Logomark } from '../../shared/Icon'
import { FormError } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Auth.module.css'

interface RequestLinkAnswer { status: string; whatsappNumber: string | null }

/**
 * Sign in without a password: his phone -> a one-time link in WhatsApp. The phone is never taken from the address
 * (no personal data in URLs). The answer is the same whether or not the number is registered.
 */
export function Login() {
  const [params] = useSearchParams()
  const location = useLocation()
  const loggedOut = (location.state as { loggedOut?: string } | null)?.loggedOut
  const [phone, setPhone] = useState('')
  const [sent, setSent] = useState(false)
  const [waNumber, setWaNumber] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [invalid, setInvalid] = useState(false)

  useEffect(() => { document.title = 'כניסה · דאד קואץ׳' }, [])

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
      const r = await api<RequestLinkAnswer>('/auth/request-link', {
        method: 'POST', body: { phone: phone.trim(), next: safeNext(params.get('next')) ?? undefined },
      })
      setWaNumber(r?.whatsappNumber ?? null)
      setSent(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const wa = coachLink(waNumber, 'היי, אני רוצה להיכנס ללוח')

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
            <h1 className={styles.title}>הקישור בדרך אליך</h1>
            <p className={styles.lead}>אם המספר רשום אצלנו, שלחנו לך קישור כניסה בוואטסאפ. הוא תקף ל-15 דקות ולכניסה אחת.</p>
            <div className={cx(ui.note)}>
              <Icon name="info" size={18} />
              <span>
                לא הגיע? כתוב לנו בוואטסאפ{waNumber ? <> ל-<span className="ltr">{displayPhone(waNumber)}</span></> : null} ונסה שוב.
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
          <form onSubmit={submit} noValidate className={styles.form}>
            <h1 className={styles.title}>כניסה ללוח שלך</h1>
            <p className={styles.lead}>בלי סיסמה. כתוב את מספר הטלפון שאיתו אתה מדבר עם המאמן, ונשלח לך קישור כניסה בוואטסאפ.</p>
            {loggedOut && (
              <p className={cx(ui.note)}>
                <Icon name="check" size={18} />
                {loggedOut === 'all' ? 'יצאת מהחשבון בכל המכשירים.' : loggedOut === 'deleted' ? 'בקשת המחיקה התקבלה. הנתונים שלך יימחקו.' : 'יצאת מהחשבון.'}
              </p>
            )}
            <div className={ui.field}>
              <label className={ui.label} htmlFor="phone">מספר טלפון</label>
              <input id="phone" className={ui.input} type="tel" inputMode="tel" autoComplete="tel" dir="ltr"
                     placeholder="050-1234567" value={phone} onChange={(e) => setPhone(e.target.value)}
                     aria-invalid={invalid} aria-describedby={invalid ? 'phone-err' : undefined} required />
              {invalid && <span id="phone-err" className={ui.fieldError}>צריך מספר טלפון מלא.</span>}
            </div>
            <FormError error={error} />
            <button type="submit" className={cx(ui.btn, ui.primary, ui.block)} disabled={busy}>
              {busy ? 'שולח…' : 'שלחו לי קישור כניסה'}
            </button>
          </form>
        )}
      </div>
      <p className={styles.foot}>
        דאד קואץ׳ · זמן אמיתי עם הילדים, כל שבוע · <Link to="/privacy">פרטיות</Link> · <Link to="/terms">תנאים</Link>
      </p>
    </main>
  )
}
