import { Link } from 'react-router'
import { useOverview } from '../../lib/admin'
import { dayMonth } from '../../lib/format'
import { FATHER_STATUS } from '../../lib/labels'
import { Page } from '../../shared/Page'
import { ErrorState, SkeletonTiles } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Admin.module.css'

export function AdminOverview() {
  const o = useOverview()
  return (
    <Page title="סקירה" subtitle={o.data ? <>השבוע שהתחיל ב-<span className="num">{dayMonth(o.data.weekStart)}</span> (שעון ישראל)</> : undefined}>
      {o.isPending ? <SkeletonTiles count={6} /> : o.error ? <ErrorState error={o.error} onRetry={() => o.refetch()} /> : (
        <>
          <div className={ui.stats}>
            <Link to="/admin/fathers" className={cx(ui.stat)}>
              <div className={ui.statNum}>{Object.values(o.data.fathersByStatus).reduce((a, b) => a + b, 0)}</div>
              <div className={ui.statCap}>אבות</div>
            </Link>
            <div className={cx(ui.stat, ui.statPlain)}><div className={ui.statNum}>{o.data.activeThisWeek}</div><div className={ui.statCap}>כתבו למאמן השבוע</div></div>
            <div className={cx(ui.stat, ui.statPlain)}><div className={ui.statNum}>{o.data.goalsMet} / {o.data.goalsSet}</div><div className={ui.statCap}>יעדים שבועיים שהושגו</div></div>
            <div className={cx(ui.stat, ui.statPlain)}><div className={ui.statNum}>{o.data.sessionsCompletedThisWeek} / {o.data.sessionsThisWeek}</div><div className={ui.statCap}>מפגשים השבוע (היו / נקבעו)</div></div>
            <Link to="/admin/undelivered" className={cx(ui.stat, o.data.failedDeliveries7d > 0 ? ui.statDanger : ui.statPlain)}>
              <div className={ui.statNum}>{o.data.failedDeliveries7d}</div><div className={ui.statCap}>הודעות שלא נמסרו (7 ימים)</div>
            </Link>
            <Link to="/admin/deletions" className={cx(ui.stat, o.data.pendingDeletions > 0 ? ui.statGlow : ui.statPlain)}>
              <div className={ui.statNum}>{o.data.pendingDeletions}</div><div className={ui.statCap}>מחיקות בפלטפורמה בתהליך</div>
            </Link>
          </div>
          <section className={ui.card}>
            <div className={ui.cardHead}><h2 className={ui.cardTitle}>אבות לפי מצב</h2></div>
            <div className={styles.statusLine}>
              {Object.entries(o.data.fathersByStatus).map(([status, n]) => (
                <Link key={status} to={`/admin/fathers?status=${status}`} className={cx(ui.chip, ui.chipTeal)}>
                  {FATHER_STATUS[status] ?? status} · <span className="num">{n}</span>
                </Link>
              ))}
              {Object.keys(o.data.fathersByStatus).length === 0 && <span className={ui.muted}>עוד אין אבות.</span>}
            </div>
          </section>
        </>
      )}
    </Page>
  )
}
