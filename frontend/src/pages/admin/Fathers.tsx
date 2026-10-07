import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useFathers } from '../../lib/admin'
import { beltName } from '../../lib/belts'
import { dateTime, displayPhone } from '../../lib/format'
import { FATHER_STATUS } from '../../lib/labels'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Admin.module.css'

export function Fathers() {
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')
  const status = params.get('status') ?? ''
  const fathers = useFathers(params.get('q') ?? '', status)
  return (
    <Page title="אבות" subtitle="חיפוש לפי שם או מספר">
      <form className={styles.filters} role="search" onSubmit={(e) => { e.preventDefault(); setParams((p) => { p.set('q', q); return p }) }}>
        <div className={ui.field}>
          <label className={ui.label} htmlFor="q">חיפוש</label>
          <input id="q" className={ui.input} value={q} onChange={(e) => setQ(e.target.value)} placeholder="שם, או ספרות מהטלפון" />
        </div>
        <div className={ui.field}>
          <label className={ui.label} htmlFor="status">מצב</label>
          <select id="status" className={ui.select} value={status} onChange={(e) => setParams((p) => { p.set('status', e.target.value); return p })}>
            <option value="">הכול</option>
            {Object.entries(FATHER_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <button type="submit" className={cx(ui.btn, ui.secondary)}><Icon name="search" size={18} /> חיפוש</button>
      </form>
      <section className={ui.card}>
        {fathers.isPending ? <Skeleton lines={5} card={false} /> : fathers.error ? <ErrorState error={fathers.error} onRetry={() => fathers.refetch()} />
          : fathers.data.length === 0 ? <EmptyState title="לא נמצאו אבות" icon="users" /> : (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <caption className="visually-hidden">אבות</caption>
              <thead><tr><th>שם</th><th>טלפון</th><th>מצב</th><th>חגורה</th><th>מפגשים</th><th>ילדים</th><th>יומן</th><th>פעילות אחרונה</th></tr></thead>
              <tbody>
                {fathers.data.map((f) => (
                  <tr key={f.id}>
                    <td><Link to={`/admin/fathers/${f.id}`} className={ui.rowBtn}>{f.name || 'ללא שם'}</Link></td>
                    <td className="ltr">{displayPhone(f.phone)}</td>
                    <td><span className={cx(ui.chip, f.status === 'ACTIVE' && ui.chipSuccess, f.status === 'PAUSED' && ui.chipWarning)}>{FATHER_STATUS[f.status] ?? f.status}</span>
                      {f.deletionPending && <span className={cx(ui.chip, ui.chipDanger)}> במחיקה</span>}</td>
                    <td>{beltName(f.belt).replace('חגורה ', '')}</td>
                    <td className="num">{f.completed}</td>
                    <td className="num">{f.children}</td>
                    <td>{f.calendarConnected ? <Icon name="check" size={16} /> : '—'}</td>
                    <td>{dateTime(f.lastActivity)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </Page>
  )
}
