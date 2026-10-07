import type { ReactNode } from 'react'
import { Link, Navigate, Outlet, useLocation } from 'react-router'
import { ApiError } from '../lib/api'
import { can, homeFor, useMe } from '../lib/session'
import { Logomark } from '../shared/Icon'
import { ErrorState } from '../shared/States'
import { AppShell } from './AppShell'
import { ADMIN_NAV, FATHER_NAV, visibleNav } from './nav'
import styles from './Layouts.module.css'

/** Signed in, or off to /login (with the way back). The server still checks every call. */
function useSession() {
  const me = useMe()
  const location = useLocation()
  if (me.isPending) return { state: 'loading' as const }
  if (me.error) {
    if (me.error instanceof ApiError && me.error.status === 401) {
      const next = location.pathname + location.search
      return { state: 'redirect' as const, to: `/login?next=${encodeURIComponent(next)}` }
    }
    return { state: 'error' as const, error: me.error, retry: () => me.refetch() }
  }
  return { state: 'ok' as const, me: me.data! }
}

export function FullScreenLoading() {
  return (
    <div className={styles.full} role="status" aria-live="polite">
      <div className={styles.pulse}><Logomark size={64} /></div>
      <span className="visually-hidden">טוען…</span>
    </div>
  )
}

function FullScreenError({ error, retry }: { error: unknown; retry: () => void }) {
  return (
    <div className={styles.full}>
      <div className={styles.fullBox}>
        <Logomark size={56} />
        <ErrorState error={error} onRetry={retry} />
      </div>
    </div>
  )
}

export function RootRedirect() {
  const s = useSession()
  if (s.state === 'loading') return <FullScreenLoading />
  if (s.state === 'redirect') return <Navigate to="/login" replace />
  if (s.state === 'error') return <FullScreenError error={s.error} retry={s.retry} />
  return <Navigate to={homeFor(s.me)} replace />
}

export function FatherLayout() {
  const s = useSession()
  if (s.state === 'loading') return <FullScreenLoading />
  if (s.state === 'redirect') return <Navigate to={s.to} replace />
  if (s.state === 'error') return <FullScreenError error={s.error} retry={s.retry} />
  if (!can(s.me, 'father')) return <Navigate to={homeFor(s.me)} replace />
  return (
    <AppShell nav={visibleNav(FATHER_NAV, s.me.capabilities)}>
      <Outlet />
    </AppShell>
  )
}

export function AdminLayout() {
  const s = useSession()
  if (s.state === 'loading') return <FullScreenLoading />
  if (s.state === 'redirect') return <Navigate to={s.to} replace />
  if (s.state === 'error') return <FullScreenError error={s.error} retry={s.retry} />
  if (!can(s.me, 'admin')) return <Navigate to={homeFor(s.me)} replace />
  return (
    <AppShell nav={ADMIN_NAV}>
      <Outlet />
    </AppShell>
  )
}

export function NotFound({ inShell }: { inShell?: boolean }): ReactNode {
  const body = (
    <div className={styles.fullBox}>
      <Logomark size={56} />
      <h1 className={styles.nfTitle}>לא מצאתי את הדף הזה</h1>
      <p className={styles.nfText}>אולי הקישור ישן, או שאין לך גישה אליו.</p>
      <Link to="/">לדף הבית</Link>
    </div>
  )
  return inShell ? body : <div className={styles.full}>{body}</div>
}
