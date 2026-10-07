import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'
import { can, homeFor, safeNext } from '../../lib/session'
import type { Me } from '../../lib/types'
import { coachLink, DASHBOARD_WORD } from '../../lib/whatsapp'
import { Icon, Logomark } from '../../shared/Icon'
import { ErrorState } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Auth.module.css'

/**
 * `/auth/consume#token=…&next=/sessions` - the button the coach sends on WhatsApp (D-027). The token is reusable, so
 * every tap signs in again; it is still sent once per visit and never stays in the address bar or history. A token
 * that no longer works (revoked, expired, cut short) sends someone already signed in on this device to his page, and
 * anyone else to a short way to get a new button.
 */
export function Consume() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const started = useRef(false)
  const [error, setError] = useState<unknown>(null)
  const [invalid, setInvalid] = useState(false)

  // A second button opened in the same tab only changes the hash: start over with the new token.
  useEffect(() => {
    const onHash = () => { if (window.location.hash.includes('token=')) window.location.reload() }
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  useEffect(() => {
    document.title = 'נכנס… · דאד קואץ׳'
    if (started.current) return
    started.current = true
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ''))
    const token = hash.get('token')
    const next = safeNext(hash.get('next'))
    // The token never stays in the address bar or history.
    window.history.replaceState(null, '', window.location.pathname)

    /** No usable token: whoever is still signed in here goes to his page; everyone else sees how to get a button. */
    const fallBack = () => api<Me>('/me')
      .then((me) => {
        qc.setQueryData(['me'], me)
        navigate(homeFor(me), { replace: true })
      })
      .catch(() => setInvalid(true))

    if (!token) {
      void fallBack()
      return
    }
    api<Me>('/auth/consume-link', { method: 'POST', body: { token } })
      .then((me) => {
        qc.clear()
        qc.setQueryData(['me'], me)
        const target = next && (next.startsWith('/admin') ? can(me, 'admin') : can(me, 'father')) ? next : homeFor(me)
        navigate(target, { replace: true })
      })
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) void fallBack()
        else setError(e)
      })
  }, [navigate, qc])

  return (
    <main className={styles.screen}>
      <div className={styles.card}>
        <div className={styles.brand}>
          <Logomark size={52} />
          <span>דאד קואץ׳</span>
        </div>
        {invalid ? <NewButton /> : error ? (
          <>
            <h1 className={styles.title}>לא הצלחתי להכניס אותך</h1>
            <ErrorState error={error} />
            <Link to="/login">לדף הכניסה</Link>
          </>
        ) : (
          <div role="status" aria-live="polite" className={styles.form}>
            <h1 className={styles.title}>רגע, מכניס אותך…</h1>
            <div className={styles.dots} aria-hidden="true"><span /><span /><span /></div>
          </div>
        )}
      </div>
    </main>
  )
}

/** The button he tapped no longer works: one line on how to get a new one, the phone form only behind a link. */
function NewButton() {
  useEffect(() => { document.title = 'כניסה · דאד קואץ׳' }, [])
  const info = useQuery({
    queryKey: ['sign-in-info'], queryFn: () => api<{ whatsappNumber: string | null }>('/auth/sign-in-info'), staleTime: Infinity,
  })
  const wa = coachLink(info.data?.whatsappNumber, DASHBOARD_WORD)
  return (
    <div className={styles.form}>
      <h1 className={styles.title}>הכפתור הזה כבר לא פעיל</h1>
      <p className={styles.lead}>
        אין בעיה: כתוב למאמן <strong>"{DASHBOARD_WORD}"</strong> בוואטסאפ, ותקבל כפתור חדש שעובד תמיד.
      </p>
      {wa ? (
        <a className={cx(ui.btn, ui.whatsapp, ui.block)} href={wa} target="_blank" rel="noreferrer">
          <Icon name="whatsapp" size={20} /> לכתוב "{DASHBOARD_WORD}" למאמן
        </a>
      ) : null}
      <Link className={styles.secondaryWay} to="/login">דרכים אחרות להיכנס</Link>
    </div>
  )
}
