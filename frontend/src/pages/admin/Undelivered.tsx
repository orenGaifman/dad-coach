import { Link } from 'react-router'
import { useUndelivered } from '../../lib/admin'
import { dateTime } from '../../lib/format'
import { DELIVERY_KIND, reasonLabel } from '../../lib/labels'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'

export function Undelivered() {
  const rows = useUndelivered()
  return (
    <Page title="הודעות שלא נמסרו" subtitle="7 הימים האחרונים: הודעות מאמן מתוזמנות וקישורי כניסה">
      <section className={ui.card}>
        {rows.isPending ? <Skeleton lines={4} card={false} /> : rows.error ? <ErrorState error={rows.error} onRetry={() => rows.refetch()} />
          : rows.data.length === 0 ? <EmptyState title="הכול נמסר" icon="check">אין הודעות שנכשלו בשבוע האחרון.</EmptyState> : (
          <div className={ui.tableWrap}><table className={ui.table}>
            <thead><tr><th>מתי</th><th>אבא</th><th>מה</th><th>סיבה</th></tr></thead>
            <tbody>{rows.data.map((r, i) => (
              <tr key={i}>
                <td>{dateTime(r.at)}</td>
                <td>{r.fatherId ? <Link to={`/admin/fathers/${r.fatherId}`}>{r.fatherName || `#${r.fatherId}`}</Link> : 'צוות'}</td>
                <td>{DELIVERY_KIND[r.kind] ?? r.kind} <span className="ltr">{r.what ?? ''}</span></td>
                <td>{reasonLabel(r.reason)}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </section>
    </Page>
  )
}
