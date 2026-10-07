import { useEffect, type ReactNode } from 'react'
import styles from './Page.module.css'

interface PageProps {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
  children: ReactNode
  /** Browser-tab title; defaults to the page title. */
  docTitle?: string
}

/** Every screen: one h1, an optional line under it, actions at the inline end. */
export function Page({ title, subtitle, actions, children, docTitle }: PageProps) {
  useEffect(() => {
    document.title = `${docTitle ?? title} · דאד קואץ׳`
  }, [docTitle, title])
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.titles}>
          <h1 className={styles.title}>{title}</h1>
          {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
        </div>
        {actions && <div className={styles.actions}>{actions}</div>}
      </header>
      {children}
    </div>
  )
}
