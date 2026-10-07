import { useState } from 'react'
import { useHome, useSessions } from '../../lib/father'
import type { Session } from '../../lib/types'
import { bookingPrompt, coachLink } from '../../lib/whatsapp'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import { SessionRow } from './SessionParts'
import styles from './Father.module.css'

type Tab = 'upcoming' | 'awaiting' | 'past'

/** מפגשים: what is coming, what waits for his "it happened", what was. Booking happens in WhatsApp. */
export function Sessions() {
  const sessions = useSessions()
  const home = useHome()
  const [tab, setTab] = useState<Tab | null>(null)
  const [child, setChild] = useState<number | 'all'>('all')
  if (sessions.isPending) return <Page title="מפגשים"><Skeleton lines={5} /></Page>
  if (sessions.error) return <Page title="מפגשים"><ErrorState error={sessions.error} onRetry={() => sessions.refetch()} /></Page>
  const v = sessions.data
  const today = home.data?.today ?? new Date().toISOString().slice(0, 10)
  const current: Tab = tab ?? (v.awaiting.length > 0 ? 'awaiting' : 'upcoming')
  const list: Session[] = v[current].filter((s) => child === 'all' || s.childId === child)
  const firstChild = v.children[0]?.name
  const link = coachLink(home.data?.coachWhatsApp, bookingPrompt(firstChild))
  const tabs: { key: Tab; label: string; count: number }[] = [
    { key: 'upcoming', label: 'קרובים', count: v.upcoming.length },
    { key: 'awaiting', label: 'מחכים לאישור', count: v.awaiting.length },
    { key: 'past', label: 'היו', count: v.past.length },
  ]
  return (
    <Page title="מפגשים" subtitle="הזמן שקבעת עם הילדים">
      <div className={ui.row}>
        <div className={ui.segmented} role="radiogroup" aria-label="אילו מפגשים">
          {tabs.map((t) => (
            <label key={t.key}>
              <input type="radio" name="tab" checked={current === t.key} onChange={() => setTab(t.key)} />
              <span>{t.label} <span className="num">({t.count})</span></span>
            </label>
          ))}
        </div>
        {v.children.length > 1 && (
          <div className={ui.segmented} role="radiogroup" aria-label="עם מי">
            <label><input type="radio" name="child" checked={child === 'all'} onChange={() => setChild('all')} /><span>כולם</span></label>
            {v.children.map((c) => (
              <label key={c.id}><input type="radio" name="child" checked={child === c.id} onChange={() => setChild(c.id)} /><span>{c.name}</span></label>
            ))}
          </div>
        )}
      </div>

      <section className={ui.card}>
        {list.length > 0 ? (
          <ul className={styles.sessions}>
            {list.map((s) => <SessionRow key={s.id} session={s} today={today} />)}
          </ul>
        ) : current === 'awaiting' ? (
          <EmptyState title="אין מה לאשר" icon="check">כל המפגשים שהיו כבר סומנו. יפה.</EmptyState>
        ) : current === 'past' ? (
          <EmptyState title="עוד אין מפגשים שהיו" icon="calendar">אחרי מפגש, סמן "היה!" - וזה יופיע כאן.</EmptyState>
        ) : (
          <EmptyState title="אין מפגשים קרובים" icon="calendar">
            כתוב למאמן: <q>{bookingPrompt(firstChild)}</q>
          </EmptyState>
        )}
      </section>

      <section className={cx(ui.card, ui.note)}>
        <Icon name="whatsapp" size={20} />
        <div className={ui.stackSm}>
          <strong>רוצה לקבוע מפגש חדש או להזיז אחד?</strong>
          <span>זה קורה בשיחה עם המאמן בוואטסאפ: הוא בודק מתי נוח, קובע, ומזכיר לך.</span>
          {link && <a className={cx(ui.btn, ui.whatsapp, ui.sm)} href={link} target="_blank" rel="noreferrer" style={{ justifySelf: 'start' }}>
            <Icon name="whatsapp" size={18} /> לקבוע בוואטסאפ
          </a>}
        </div>
      </section>
    </Page>
  )
}
