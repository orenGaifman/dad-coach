import { useEffect, useRef, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { BottomNav } from './BottomNav'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'
import type { NavItem } from './nav'
import styles from './AppShell.module.css'

/**
 * The one layout every signed-in screen renders in. Desktop: sidebar + slim top bar.
 * Mobile: top bar + bottom tab bar, only the content scrolls.
 */
export function AppShell({ nav, banner, keepSearch, children }:
  { nav: NavItem[]; banner?: ReactNode; keepSearch?: string; children: ReactNode }) {
  const bodyRef = useRef<HTMLDivElement>(null)
  const { pathname } = useLocation()
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: 0 })
    window.scrollTo({ top: 0 })
  }, [pathname])
  return (
    <div className={styles.shell}>
      <a className="skip-link" href="#main">דילוג לתוכן</a>
      <Sidebar nav={nav} keepSearch={keepSearch} />
      <div className={styles.body} ref={bodyRef}>
        <TopBar />
        {banner}
        <main id="main" className={styles.main} tabIndex={-1}>
          {children}
        </main>
      </div>
      <BottomNav nav={nav} keepSearch={keepSearch} />
    </div>
  )
}
