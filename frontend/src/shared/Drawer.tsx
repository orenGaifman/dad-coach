import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'
import styles from './Drawer.module.css'
import { cx } from './cx'

interface DrawerProps {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  wide?: boolean
}

/**
 * Side panel on desktop (inline-end edge — the left in RTL), bottom sheet on mobile. Native <dialog>
 * gives focus trapping, Escape and focus restore. The caller decides when it closes (never on failure).
 */
export function Drawer({ open, title, onClose, children, footer, wide }: DrawerProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    else if (!open && d.open) d.close()
  }, [open])
  return (
    <dialog ref={ref} className={cx(styles.drawer, wide && styles.wide)} aria-labelledby={titleId}
            onCancel={(e) => { e.preventDefault(); onClose() }}
            onClick={(e) => { if (e.target === e.currentTarget) onClose() }}>
      {open && (
        <div className={styles.inner}>
          <div className={styles.header}>
            <h2 id={titleId} className={styles.title}>{title}</h2>
            <button type="button" className={styles.close} onClick={onClose} aria-label="סגירה">
              <Icon name="x" size={20} />
            </button>
          </div>
          <div className={styles.body}>{children}</div>
          {footer && <div className={styles.footer}>{footer}</div>}
        </div>
      )}
    </dialog>
  )
}
