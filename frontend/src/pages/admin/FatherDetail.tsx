import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useFatherDetail } from '../../lib/admin'
import { beltName } from '../../lib/belts'
import { errorLine } from '../../lib/errors'
import { dateTime, displayPhone, duration } from '../../lib/format'
import { DELIVERY_KIND, FATHER_STATUS, PHASE_LABEL, reasonLabel, GOAL_STATUS, DELIVERY_STATUS, CHILD_STATUS } from '../../lib/labels'
import type { FatherDetail } from '../../lib/types'
import { ConfirmDialog } from '../../shared/ConfirmDialog'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { ErrorState, Skeleton } from '../../shared/States'
import { useToast } from '../../shared/Toast'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Admin.module.css'

export function FatherDetailPage() {
  const { id = '' } = useParams()
  const detail = useFatherDetail(id)
  if (detail.isPending) return <Page title="אבא"><Skeleton lines={6} /></Page>
  if (detail.error) return <Page title="אבא"><ErrorState error={detail.error} onRetry={() => detail.refetch()} /></Page>
  const d = detail.data
  const p = d.profile
  return (
    <Page title={p.name || 'ללא שם'} docTitle={p.name || 'אבא'}
          subtitle={<><span className="ltr">{displayPhone(p.phone)}</span> · {FATHER_STATUS[p.status] ?? p.status} · {beltName(p.belt)}</>}
          actions={<Link to={`/admin/fathers/${p.id}/home`} className={cx(ui.btn, ui.secondary)}><Icon name="eye" size={18} /> כך הוא רואה את הלוח</Link>}>
      <Link to="/admin/fathers" className={ui.backLink}><Icon name="chevronStart" size={16} /> כל האבות</Link>
      <div className={ui.grid2}>
        <section className={ui.card}>
          <div className={ui.cardHead}><h2 className={ui.cardTitle}>פרופיל</h2></div>
          <dl className={styles.kv}>
            <dt>מזהה</dt><dd className="num">{p.id}</dd>
            <dt>הצטרף</dt><dd>{dateTime(p.createdAt)}</dd>
            <dt>אינטראקציה אחרונה</dt><dd>{dateTime(p.lastInteractionAt)}</dd>
            <dt>אזור זמן</dt><dd className="ltr">{p.timezone}</dd>
            <dt>מפגשים שהיו</dt><dd className="num">{p.completed}</dd>
            <dt>שבועות ברצף</dt><dd className="num">{p.streakWeeks} (שיא {p.longestStreakWeeks})</dd>
            <dt>יומן Google</dt><dd>{p.calendarConnected ? 'מחובר' : 'לא מחובר'}</dd>
            <dt>מצב בזרימה</dt><dd className="ltr">{p.workflowState ?? '—'} / {p.onboardingState ?? '—'}</dd>
            <dt>חיבורים פעילים ללוח</dt><dd className="num">{d.liveDashboardSessions}</dd>
          </dl>
        </section>
        <section className={ui.card}>
          <div className={ui.cardHead}><h2 className={ui.cardTitle}>ילדים</h2></div>
          {d.children.length === 0 ? <p className={ui.muted}>אין ילדים.</p> : (
            <ul className={ui.list}>
              {d.children.map((c) => <li key={c.id} className={ui.listItem}><strong>{c.name}</strong><span className={ui.muted}>גיל {c.age}</span>
                {c.status !== 'ACTIVE' && <span className={ui.chip}>{CHILD_STATUS[c.status] ?? c.status}</span>}</li>)}
            </ul>
          )}
        </section>
      </div>

      <section className={ui.card}>
        <div className={ui.cardHead}><h2 className={ui.cardTitle}>יעדים שבועיים</h2></div>
        {d.goals.length === 0 ? <p className={ui.muted}>אין יעדים.</p> : (
          <div className={ui.tableWrap}><table className={ui.table}>
            <thead><tr><th>שבוע</th><th>יעד</th><th>זוכה</th><th>מצב</th></tr></thead>
            <tbody>{d.goals.map((g) => <tr key={g.weekStart}><td className="num">{g.weekStart}</td><td>{duration(g.targetHours * 60)}</td>
              <td>{duration(g.creditedMinutes)}</td><td>{GOAL_STATUS[g.status] ?? g.status}</td></tr>)}</tbody>
          </table></div>
        )}
      </section>

      <section className={ui.card}>
        <div className={ui.cardHead}><h2 className={ui.cardTitle}>מפגשים</h2></div>
        {d.sessions.length === 0 ? <p className={ui.muted}>אין מפגשים.</p> : (
          <div className={ui.tableWrap}><table className={ui.table}>
            <thead><tr><th>ילד</th><th>התחלה</th><th>סיום</th><th>מצב</th><th>הערה</th></tr></thead>
            <tbody>{d.sessions.map((s) => <tr key={s.id}><td>{s.childName ?? '—'}</td><td>{dateTime(s.start)}</td><td>{dateTime(s.end)}</td>
              <td>{PHASE_LABEL[s.phase] ?? s.phase}</td><td>{s.notes ?? ''}</td></tr>)}</tbody>
          </table></div>
        )}
      </section>

      <section className={ui.card}>
        <div className={ui.cardHead}><h2 className={ui.cardTitle}>הודעות מתוזמנות וקישורי כניסה</h2></div>
        {d.deliveries.length === 0 && d.loginLinks.length === 0 ? <p className={ui.muted}>אין.</p> : (
          <div className={ui.tableWrap}><table className={ui.table}>
            <thead><tr><th>מתי</th><th>מה</th><th>מצב</th><th>סיבה</th></tr></thead>
            <tbody>
              {d.deliveries.map((r, i) => <tr key={`d${i}`}><td>{dateTime(r.at)}</td><td>{DELIVERY_KIND[r.kind]} <span className="ltr">{r.what ?? ''}</span></td>
                <td className={r.status === 'FAILED' ? styles.bad : styles.ok}>{DELIVERY_STATUS[r.status] ?? r.status}</td><td>{reasonLabel(r.reason)}</td></tr>)}
              {d.loginLinks.map((l, i) => <tr key={`l${i}`}><td>{dateTime(l.createdAt)}</td><td>קישור כניסה{l.usedAt ? ' (נוצל)' : ''}</td>
                <td className={l.deliveryStatus === 'FAILED' ? styles.bad : styles.ok}>{l.deliveryStatus}</td><td>{reasonLabel(l.deliveryError)}</td></tr>)}
            </tbody>
          </table></div>
        )}
      </section>

      <PlatformPanel id={String(p.id)} />
      <Lifecycle d={d} />
    </Page>
  )
}

interface PlatformPanelData {
  configured: boolean
  reachable?: boolean
  error?: string
  instances?: Record<string, unknown>[]
  current?: Record<string, unknown>
  scheduledTransitions?: unknown
  messages?: { role?: string; direction?: string; content?: string; createdAt?: string }[]
}

/** Read-through to the platform's admin API (nothing stored or changed here). */
function PlatformPanel({ id }: { id: string }) {
  const [open, setOpen] = useState(false)
  const panel = useQuery({ queryKey: ['admin', 'platform', id], queryFn: () => api<PlatformPanelData>(`/admin/fathers/${id}/platform`), enabled: open })
  const transitions = Array.isArray(panel.data?.scheduledTransitions) ? panel.data?.scheduledTransitions as Record<string, unknown>[]
    : ((panel.data?.scheduledTransitions as { transitions?: Record<string, unknown>[] } | undefined)?.transitions ?? [])
  return (
    <section className={ui.card}>
      <div className={ui.cardHead}>
        <h2 className={ui.cardTitle}>בפלטפורמה</h2>
        {!open && <button type="button" className={cx(ui.btn, ui.secondary, ui.sm)} onClick={() => setOpen(true)}>טעינה מהפלטפורמה</button>}
      </div>
      {!open ? <p className={ui.muted}>מופעי הזרימה, המצב הנוכחי, טריגרים מתוזמנים והודעות אחרונות - קריאה בלבד.</p>
        : panel.isPending ? <Skeleton lines={3} card={false} />
        : panel.error ? <ErrorState error={panel.error} onRetry={() => panel.refetch()} compact />
        : !panel.data.configured ? <p className={ui.note}><Icon name="info" size={18} /> לא מוגדר: חסר WORKFLOW_PLATFORM_ADMIN_API_KEY.</p>
        : !panel.data.reachable ? <p className={cx(ui.note, ui.noteDanger)}><Icon name="alert" size={18} /> הפלטפורמה לא ענתה ({panel.data.error}).</p>
        : (
          <div className={ui.stack}>
            <dl className={styles.kv}>
              <dt>מופעים</dt><dd className="num">{panel.data.instances?.length ?? 0}</dd>
              <dt>מצב נוכחי</dt><dd className="ltr">{String(panel.data.current?.currentStateKey ?? panel.data.current?.currentState ?? '—')}</dd>
              <dt>סטטוס</dt><dd className="ltr">{String(panel.data.current?.status ?? '—')}</dd>
              <dt>טריגרים מתוזמנים</dt><dd className="num">{transitions.length}</dd>
            </dl>
            {transitions.length > 0 && (
              <ul className={ui.list}>
                {transitions.slice(0, 10).map((t, i) => <li key={i} className={ui.listItem}>
                  <span className="ltr">{String(t.targetStateKey ?? t.targetState ?? t.id ?? '')}</span>
                  <span className={ui.muted}>{dateTime(String(t.scheduledFor ?? t.fireAt ?? t.scheduledAt ?? ''))}</span>
                  <span className={ui.chip}>{String(t.status ?? '')}</span></li>)}
              </ul>
            )}
            {(panel.data.messages?.length ?? 0) > 0 && (
              <ul className={styles.messages}>
                {panel.data.messages!.map((m, i) => {
                  const out = (m.role ?? m.direction ?? '').toString().toUpperCase().match(/ASSISTANT|OUT/)
                  return <li key={i} className={cx(styles.msg, out && styles.msgOut)}>{m.content}
                    <span className={styles.msgMeta}>{dateTime(m.createdAt)}</span></li>
                })}
              </ul>
            )}
          </div>
        )}
    </section>
  )
}

/** Deactivate -> typed permanent delete (playbook §43). */
function Lifecycle({ d }: { d: FatherDetail }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const toast = useToast()
  const [deleting, setDeleting] = useState(false)
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const id = d.profile.id
  const paused = d.profile.status === 'PAUSED'
  async function call(path: string, done: string) {
    setBusy(true)
    try {
      await api(path, { method: 'POST' })
      await qc.invalidateQueries({ queryKey: ['admin'] })
      toast(done)
    } catch (e) {
      toast(errorLine(e), 'error')
    } finally {
      setBusy(false)
    }
  }
  async function remove() {
    setBusy(true)
    setError(null)
    try {
      await api(`/admin/fathers/${id}`, { method: 'DELETE', body: { confirmation: typed.trim() } })
      await qc.invalidateQueries({ queryKey: ['admin'] })
      toast('נמחק. המחיקה בפלטפורמה ממשיכה ברקע.')
      navigate('/admin/deletions', { replace: true })
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }
  return (
    <section className={cx(ui.card, styles.dangerZone)}>
      <div className={ui.cardHead}><h2 className={ui.cardTitle}>השבתה ומחיקה</h2></div>
      {d.deletion && !d.deletion.completedAt && <p className={cx(ui.note, ui.noteGlow)}><Icon name="info" size={18} /> מחיקה בפלטפורמה בתהליך (ניסיונות: {d.deletion.attempts}).</p>}
      <p className={ui.muted} style={{ marginBlockEnd: 12 }}>
        השבתה: האבא לא יכול להיכנס ללוח וכל החיבורים שלו נסגרים (השיחה בוואטסאפ לא נעצרת). מחיקה לצמיתות - רק אחרי השבתה.
      </p>
      <div className={ui.row}>
        {paused
          ? <button type="button" className={cx(ui.btn, ui.ghost)} disabled={busy} onClick={() => call(`/admin/fathers/${id}/reactivate`, 'הופעל מחדש.')}>הפעלה מחדש</button>
          : <button type="button" className={cx(ui.btn, ui.ghost)} disabled={busy || d.profile.status === 'DELETED'} onClick={() => call(`/admin/fathers/${id}/deactivate`, 'הושבת.')}>השבתה</button>}
        <button type="button" className={cx(ui.btn, ui.dangerGhost)} disabled={!paused || busy} onClick={() => { setTyped(''); setError(null); setDeleting(true) }}>
          <Icon name="trash" size={18} /> מחיקה לצמיתות
        </button>
      </div>
      <ConfirmDialog open={deleting} title="מחיקה לצמיתות" confirmLabel="למחוק" danger busy={busy} error={error}
                     confirmDisabled={typed.trim() !== d.deleteConfirmation} onClose={() => setDeleting(false)} onConfirm={remove}>
        <p>כל הנתונים שלו ב-Dad Coach יימחקו עכשיו, ובקשת מחיקה תישלח לפלטפורמה (שיחות, מופעים, טריגרים).</p>
        <div className={ui.field}>
          <label className={ui.label} htmlFor="confirm">כדי לאשר, הקלד <strong>{d.deleteConfirmation}</strong></label>
          <input id="confirm" className={ui.input} value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
        </div>
      </ConfirmDialog>
    </section>
  )
}

