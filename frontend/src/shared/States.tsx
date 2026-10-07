import type { ReactNode } from 'react'
import { correlationOf, errorText } from '../lib/errors'
import { Icon } from './Icon'
import ui from './ui.module.css'
import styles from './States.module.css'
import { cx } from './cx'

/** A quiet placeholder shaped like the content that is coming. */
export function Skeleton({ lines = 3, card = true, label = 'טוען…' }: { lines?: number; card?: boolean; label?: string }) {
  return (
    <div className={cx(card && ui.card, styles.skeleton)} role="status" aria-live="polite">
      <span className="visually-hidden">{label}</span>
      <div className={styles.barWide} />
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className={styles.bar} style={{ inlineSize: `${90 - ((i * 17) % 40)}%` }} />
      ))}
    </div>
  )
}

export function SkeletonTiles({ count = 3 }: { count?: number }) {
  return (
    <div className={ui.stats} role="status" aria-live="polite">
      <span className="visually-hidden">טוען…</span>
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className={cx(ui.stat, ui.statPlain, styles.skeleton)}>
          <div className={styles.barNum} />
          <div className={styles.bar} style={{ inlineSize: '60%' }} />
        </div>
      ))}
    </div>
  )
}

export function EmptyState({ title, children, action, icon = 'sparkle' }:
  { title: string; children?: ReactNode; action?: ReactNode; icon?: string }) {
  return (
    <div className={styles.empty}>
      <span className={styles.emptyIcon}><Icon name={icon} size={22} /></span>
      <h3 className={styles.emptyTitle}>{title}</h3>
      {children && <p className={styles.emptyText}>{children}</p>}
      {action && <div className={styles.emptyAction}>{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry, compact }: { error: unknown; onRetry?: () => void; compact?: boolean }) {
  const cid = correlationOf(error)
  return (
    <div className={cx(styles.error, compact && styles.errorCompact)} role="alert">
      <Icon name="alert" size={20} />
      <div className={styles.errorBody}>
        <p className={styles.errorText}>{errorText(error)}</p>
        {cid && <p className={styles.errorCid}>קוד לבירור: <span className="ltr">{cid}</span></p>}
      </div>
      {onRetry && (
        <button type="button" className={cx(ui.btn, ui.ghost, ui.sm)} onClick={onRetry}>
          <Icon name="refresh" size={16} /> לנסות שוב
        </button>
      )}
    </div>
  )
}

/** An inline error for forms and dialogs (the dialog stays open on failure). */
export function FormError({ error }: { error: unknown }) {
  if (!error) return null
  return <ErrorState error={error} compact />
}
