import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { can, homeFor, safeNext } from '../../lib/session'
import type { Me } from '../../lib/types'
import { Logomark } from '../../shared/Icon'
import { ErrorState } from '../../shared/States'
import styles from './Auth.module.css'

/** `/auth/consume#token=…&next=/sessions` — the token is single-use, so it is sent exactly once. */
export function Consume() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const started = useRef(false)
  const [error, setError] = useState<unknown>(null)
  const [missing, setMissing] = useState(false)

  // A second link opened in the same tab only changes the hash: start over with the new token.
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
    if (!token) {
      setMissing(true)
      return
    }
    api<Me>('/auth/consume-link', { method: 'POST', body: { token } })
      .then((me) => {
        qc.clear()
        qc.setQueryData(['me'], me)
        const target = next && (next.startsWith('/admin') ? can(me, 'admin') : can(me, 'father')) ? next : homeFor(me)
        navigate(target, { replace: true })
      })
      .catch(setError)
  }, [navigate, qc])

  return (
    <main className={styles.screen}>
      <div className={styles.card}>
        <div className={styles.brand}>
          <Logomark size={52} />
          <span>דאד קואץ׳</span>
        </div>
        {missing ? (
          <>
            <h1 className={styles.title}>הקישור לא שלם</h1>
            <p className={styles.lead}>פתח שוב את הקישור מהוואטסאפ, או בקש קישור חדש.</p>
            <Link to="/login">לבקש קישור חדש</Link>
          </>
        ) : error ? (
          <>
            <h1 className={styles.title}>לא הצלחתי להכניס אותך</h1>
            <ErrorState error={error} />
            <Link to="/login">לבקש קישור חדש</Link>
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
