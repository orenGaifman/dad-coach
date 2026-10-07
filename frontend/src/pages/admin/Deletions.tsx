import { Link } from 'react-router'
import { useDeletions } from '../../lib/admin'
import { dateTime, displayPhone } from '../../lib/format'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'

export function Deletions() {
  const d = useDeletions()
  return (
    <Page title="השבתה ומחיקה" subtitle="קודם משביתים, אחר כך מוחקים - עם אישור בהקלדה">
      {d.isPending ? <Skeleton lines={4} /> : d.error ? <ErrorState error={d.error} onRetry={() => d.refetch()} /> : (
        <>
          <div className={ui.stats}>
            <div className={cx(ui.stat, d.data.pending > 0 ? ui.statGlow : ui.statPlain)}><div className={ui.statNum}>{d.data.pending}</div>
              <div className={ui.statCap}>מחיקות בפלטפורמה בתהליך</div></div>
            <div className={cx(ui.stat, ui.statPlain)}><div className={ui.statNum}>{d.data.deactivated.length}</div><div className={ui.statCap}>אבות מושבתים</div></div>
          </div>
          <section className={ui.card}>
            <div className={ui.cardHead}><h2 className={ui.cardTitle}>מושבתים (אפשר למחוק)</h2></div>
            {d.data.deactivated.length === 0 ? <EmptyState title="אין אבות מושבתים" icon="users" /> : (
              <ul className={ui.list}>{d.data.deactivated.map((f) => (
                <li key={f.id} className={ui.listItem}><Link to={`/admin/fathers/${f.id}`}>{f.name || 'ללא שם'}</Link>
                  <span className="ltr">{displayPhone(f.phone)}</span></li>
              ))}</ul>
            )}
          </section>
          <section className={ui.card}>
            <div className={ui.cardHead}><h2 className={ui.cardTitle}>מחיקות שממתינות לאישור הפלטפורמה</h2></div>
            {d.data.rows.length === 0 ? <EmptyState title="אין מחיקות פתוחות" icon="check" /> : (
              <div className={ui.tableWrap}><table className={ui.table}>
                <thead><tr><th>אבא #</th><th>התבקש</th><th>ניסיונות</th><th>ניסיון הבא</th><th>שגיאה אחרונה</th><th>מחיקה מקומית אחרי</th></tr></thead>
                <tbody>{d.data.rows.map((r) => <tr key={r.fatherId}><td className="num">{r.fatherId}</td><td>{dateTime(r.requestedAt)}</td>
                  <td className="num">{r.attempts}</td><td>{dateTime(r.nextAttemptAt)}</td><td className="ltr">{r.lastError ?? '—'}</td>
                  <td>{r.purgeLocal ? 'כן' : 'כבר נמחק'}</td></tr>)}</tbody>
              </table></div>
            )}
          </section>
        </>
      )}
    </Page>
  )
}
