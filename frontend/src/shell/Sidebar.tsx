import { Link, NavLink, useLocation } from 'react-router'
import { Icon, Logomark } from '../shared/Icon'
import { cx } from '../shared/cx'
import { isActive, type NavItem } from './nav'
import styles from './Sidebar.module.css'

export function Sidebar({ nav, keepSearch }: { nav: NavItem[]; keepSearch?: string }) {
  const { pathname } = useLocation()
  const home = nav[0]
  return (
    <aside className={styles.sidebar} aria-label="ניווט ראשי">
      <Link to={home.to + (keepSearch ?? '')} className={styles.brand}>
        <Logomark size={36} />
        <span>Dad Coach</span>
      </Link>
      <nav className={styles.nav}>
        {nav.map((item) => {
          const active = isActive(item, pathname)
          return (
            <NavLink key={item.to} to={item.to + (keepSearch ?? '')} end={item.end}
                     className={cx(styles.link, active && styles.active)} aria-current={active ? 'page' : undefined}>
              <Icon name={item.icon} size={20} />
              <span>{item.label}</span>
            </NavLink>
          )
        })}
      </nav>
    </aside>
  )
}
