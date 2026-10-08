import { useState } from 'react'
import { errorLine } from '../../lib/errors'
import { useCancelSession, useConfirmSession } from '../../lib/father'
import { confirmedLine } from '../../lib/format'
import { dayLabel, duration } from '../../lib/format'
import type { Phase, Session } from '../../lib/types'
import { useScreenOwner } from '../../lib/viewAs'
import { ConfirmDialog } from '../../shared/ConfirmDialog'
import { Drawer } from '../../shared/Drawer'
import { Icon } from '../../shared/Icon'
import { useToast } from '../../shared/Toast'
import { FormError } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Father.module.css'

const PHASES: Record<Phase, { label: string; chip: string }> = {
  UPCOMING: { label: 'מתוכנן', chip: ui.chipTeal },
  IN_PROGRESS: { label: 'עכשיו', chip: ui.chipGlow },
  AWAITING_CONFIRMATION: { label: 'מחכה לאישור', chip: ui.chipWarning },
  COMPLETED: { label: 'היה', chip: ui.chipSuccess },
  CANCELLED: { label: 'בוטל', chip: '' },
  MISSED: { label: 'לא יצא', chip: '' },
}

export function PhaseChip({ phase }: { phase: Phase }) {
  const p = PHASES[phase]
  return <span className={cx(ui.chip, p.chip)}>{p.label}</span>
}

/** One session: who, when, how long, its phase - and, on his own screen, "it happened" / cancel. */
export function SessionRow({ session, today, actions = true }: { session: Session; today: string; actions?: boolean }) {
  const { readOnly } = useScreenOwner()
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const showActions = actions && (session.canConfirm || session.canCancel)
  return (
    <li className={styles.session}>
      <span className={cx(styles.sessionDot, styles[`dot${session.phase}`])} aria-hidden="true" />
      <div className={styles.sessionMain}>
        <div className={styles.sessionTop}>
          <strong>{session.childName ?? 'מפגש'}</strong>
          <PhaseChip phase={session.phase} />
        </div>
        <div className={styles.sessionWhen}>
          {dayLabel(session.localDate, session.weekday, today)} · <span className="num">{session.localStart}–{session.localEnd}</span> · {duration(session.durationMinutes)}
        </div>
        {session.notes && <p className={styles.sessionNote}>“{session.notes}”</p>}
      </div>
      {showActions && (
        <div className={styles.sessionActions}>
          {session.canConfirm && (
            <button type="button" className={cx(ui.btn, ui.primary, ui.sm)} disabled={readOnly} onClick={() => setConfirming(true)}>
              <Icon name="check" size={16} /> היה!
            </button>
          )}
          {session.canCancel && (
            <button type="button" className={cx(ui.btn, ui.ghost, ui.sm)} disabled={readOnly} onClick={() => setCancelling(true)}>
              {session.phase === 'UPCOMING' ? 'ביטול' : 'לא יצא'}
            </button>
          )}
        </div>
      )}
      {confirming && <ConfirmDrawer session={session} onClose={() => setConfirming(false)} />}
      {cancelling && <CancelDialog session={session} onClose={() => setCancelling(false)} />}
    </li>
  )
}

function ConfirmDrawer({ session, onClose }: { session: Session; onClose: () => void }) {
  const confirm = useConfirmSession()
  const toast = useToast()
  const [note, setNote] = useState('')
  async function save() {
    try {
      const result = await confirm.mutateAsync({ id: session.id, note })
      toast(confirmedLine(duration(session.durationMinutes), session.childName, result?.beltEarned ?? null))
      onClose()
    } catch {
      // shown in the drawer
    }
  }
  return (
    <Drawer open title={`המפגש עם ${session.childName ?? 'הילד'} היה?`} onClose={onClose}
            footer={<>
              <button type="button" className={cx(ui.btn, ui.ghost)} onClick={onClose} disabled={confirm.isPending}>עוד לא</button>
              <button type="button" className={cx(ui.btn, ui.primary)} onClick={save} disabled={confirm.isPending}>
                {confirm.isPending ? 'רגע…' : 'כן, היה'}
              </button>
            </>}>
      <p>זה ייספר ליעד של השבוע ויקדם אותך בחגורה.</p>
      <div className={ui.field}>
        <label className={ui.label} htmlFor="note">מה עשיתם? (לא חובה)</label>
        <textarea id="note" className={ui.textarea} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)}
                  placeholder="למשל: בנינו מגדל לגו ודיברנו על החברים בגן" />
        <span className={ui.hint}>המאמן רואה את זה ויכול להציע רעיונות למפגש הבא.</span>
      </div>
      <FormError error={confirm.error} />
    </Drawer>
  )
}

function CancelDialog({ session, onClose }: { session: Session; onClose: () => void }) {
  const cancel = useCancelSession()
  const toast = useToast()
  const upcoming = session.phase === 'UPCOMING'
  return (
    <ConfirmDialog open title={upcoming ? `לבטל את המפגש עם ${session.childName ?? 'הילד'}?` : 'המפגש לא יצא?'}
                   confirmLabel={upcoming ? 'כן, לבטל' : 'כן, לא יצא'} cancelLabel="חזרה" busy={cancel.isPending} error={cancel.error}
                   onClose={onClose}
                   onConfirm={async () => {
                     try {
                       await cancel.mutateAsync(session.id)
                       toast(upcoming ? 'המפגש בוטל. רוצה לקבוע זמן אחר? כתוב למאמן.' : 'עודכן. זה קורה - העיקר הפעם הבאה.')
                       onClose()
                     } catch (e) {
                       toast(errorLine(e), 'error')
                     }
                   }}>
      <p>{upcoming ? 'הוא לא ייספר לשבוע. אפשר לקבוע מחדש בוואטסאפ.' : 'הוא לא ייספר לשבוע, וזה בסדר.'}</p>
    </ConfirmDialog>
  )
}
