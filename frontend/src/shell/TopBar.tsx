import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { errorLine } from '../lib/errors'
import { can, useMe } from '../lib/session'
import { Icon, Logomark } from '../shared/Icon'
import { useToast } from '../shared/Toast'
import styles from './TopBar.module.css'

export function TopBar() {
  const { pathname } = useLocation()
  const admin = pathname.startsWith('/admin')
  return (
    <header className={styles.bar}>
      <Link to={admin ? '/admin' : '/home'} className={styles.brand} aria-label="Dad Coach - דף הבית">
        <Logomark size={30} />
        <span>Dad Coach</span>
      </Link>
      <span className={styles.area}>{admin ? 'ניהול Dad Coach' : ''}</span>
      <AccountMenu />
    </header>
  )
}

function AccountMenu() {
  const me = useMe().data
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { pathname } = useLocation()

  useEffect(() => setOpen(false), [pathname])
  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDoc); document.removeEventListener('keydown', onKey) }
  }, [open])

  if (!me) return null
  const name = me.name || (can(me, 'admin') ? 'צוות' : 'אבא')
  const initial = name.trim().charAt(0) || '·'

  async function logout(all: boolean) {
    setBusy(true)
    try {
      await api(all ? '/auth/logout-all' : '/auth/logout', { method: 'POST' })
      qc.clear()
      navigate('/login', { replace: true, state: { loggedOut: all ? 'all' : 'one' } })
    } catch (e) {
      toast(errorLine(e), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.account} ref={ref}>
      <button type="button" className={styles.trigger} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <span className={styles.avatar} aria-hidden="true">{initial}</span>
        <span className={styles.name}>{name}</span>
        <Icon name="chevronDown" size={16} />
        <span className="visually-hidden">תפריט חשבון</span>
      </button>
      {open && (
        <div className={styles.menu} role="menu">
          <div className={styles.who}>
            <strong>{name}</strong>
            {can(me, 'admin') && <span>צוות Dad Coach</span>}
          </div>
          {can(me, 'admin') && can(me, 'father') && (
            pathname.startsWith('/admin')
              ? <Link role="menuitem" className={styles.item} to="/home"><Icon name="home" size={18} /> השבוע שלי</Link>
              : <Link role="menuitem" className={styles.item} to="/admin"><Icon name="grid" size={18} /> ניהול</Link>
          )}
          <button role="menuitem" type="button" className={styles.item} disabled={busy} onClick={() => logout(false)}>
            <Icon name="logout" size={18} /> יציאה
          </button>
          <button role="menuitem" type="button" className={styles.item} disabled={busy} onClick={() => logout(true)}>
            <Icon name="shield" size={18} /> יציאה מכל המכשירים
          </button>
        </div>
      )}
    </div>
  )
}
