import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { Icon } from '../shared/Icon'
import { cx } from '../shared/cx'
import { isActive, type NavItem } from './nav'
import styles from './BottomNav.module.css'

/** Mobile tab bar: the primary destinations, the rest under "עוד" (same nav model as the sidebar). */
export function BottomNav({ nav, keepSearch }: { nav: NavItem[]; keepSearch?: string }) {
  const { pathname } = useLocation()
  const primary = nav.filter((i) => i.primary)
  const more = nav.filter((i) => !i.primary)
  const [open, setOpen] = useState(false)
  const moreActive = more.some((i) => isActive(i, pathname))
  const sheetRef = useRef<HTMLDivElement>(null)

  useEffect(() => setOpen(false), [pathname])
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('keydown', onKey)
    sheetRef.current?.querySelector<HTMLElement>('a')?.focus()
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <>
      {open && (
        <div className={styles.scrim} onClick={() => setOpen(false)}>
          <div className={styles.sheet} ref={sheetRef} id="more-sheet" onClick={(e) => e.stopPropagation()}>
            {more.map((item) => {
              const active = isActive(item, pathname)
              return (
                <Link key={item.to} to={item.to + (keepSearch ?? '')} className={cx(styles.sheetLink, active && styles.sheetActive)}
                      aria-current={active ? 'page' : undefined}>
                  <Icon name={item.icon} size={20} />
                  {item.label}
                </Link>
              )
            })}
          </div>
        </div>
      )}
      <nav className={styles.bar} aria-label="ניווט ראשי">
        {primary.map((item) => {
          const active = isActive(item, pathname)
          return (
            <Link key={item.to} to={item.to + (keepSearch ?? '')} className={cx(styles.item, active && styles.itemActive)}
                  aria-current={active ? 'page' : undefined}>
              <span className={styles.iconWrap}><Icon name={item.icon} size={22} /></span>
              <span>{item.short ?? item.label}</span>
            </Link>
          )
        })}
        {more.length > 0 && (
          <button type="button" className={cx(styles.item, (moreActive || open) && styles.itemActive)}
                  aria-expanded={open} aria-controls="more-sheet" onClick={() => setOpen((o) => !o)}>
            <span className={styles.iconWrap}><Icon name="more" size={22} /></span>
            <span>עוד</span>
          </button>
        )}
      </nav>
    </>
  )
}
