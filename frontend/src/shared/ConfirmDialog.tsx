import { useEffect, useId, useRef, type ReactNode } from 'react'
import { FormError } from './States'
import ui from './ui.module.css'
import styles from './ConfirmDialog.module.css'
import { cx } from './cx'

interface ConfirmDialogProps {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  danger?: boolean
  busy?: boolean
  error?: unknown
  confirmDisabled?: boolean
  onConfirm: () => void
  onClose: () => void
}

/** A centred confirm for irreversible or outward-facing actions. Stays open (with the error) on failure. */
export function ConfirmDialog({ open, title, children, confirmLabel, cancelLabel = 'ביטול', danger, busy, error,
                                confirmDisabled, onConfirm, onClose }: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    else if (!open && d.open) d.close()
  }, [open])
  return (
    <dialog ref={ref} className={styles.dialog} aria-labelledby={titleId}
            onCancel={(e) => { e.preventDefault(); if (!busy) onClose() }}>
      {open && (
        <div className={styles.inner}>
          <h2 id={titleId} className={styles.title}>{title}</h2>
          {children && <div className={styles.body}>{children}</div>}
          <FormError error={error} />
          <div className={styles.actions}>
            <button type="button" className={cx(ui.btn, ui.ghost)} onClick={onClose} disabled={busy}>{cancelLabel}</button>
            <button type="button" className={cx(ui.btn, danger ? ui.danger : ui.primary)} onClick={onConfirm}
                    disabled={busy || confirmDisabled}>
              {busy ? 'רגע…' : confirmLabel}
            </button>
          </div>
        </div>
      )}
    </dialog>
  )
}
