import { useAdminTraining } from '../../lib/admin'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'

/** The training catalog (training/catalog.json) and whether fathers can see it. */
export function AdminTrainingPage() {
  const t = useAdminTraining()
  return (
    <Page title="הדרכה" subtitle="הקטלוג הוא קובץ אחד; הסרטונים מוגשים מ-TRAINING_MEDIA_BASE_URL">
      {t.isPending ? <Skeleton lines={4} /> : t.error ? <ErrorState error={t.error} onRetry={() => t.refetch()} /> : (
        <>
          {!t.data.mediaConfigured && <p className={cx(ui.note, ui.noteGlow)}><Icon name="info" size={18} />
            שרת המדיה לא מוגדר, אז אבות לא רואים את עמוד ההדרכה. הסרטונים יופקו בהמשך.</p>}
          <section className={ui.card}>
            <div className={ui.tableWrap}><table className={ui.table}>
              <thead><tr><th>#</th><th>כותרת</th><th>תיאור</th><th>אורך</th><th>קובץ</th><th>פעיל</th></tr></thead>
              <tbody>{t.data.videos.map((v) => <tr key={v.slug}><td className="num">{v.order}</td>
                <td><strong>{v.title}</strong>{v.primary && <span className={cx(ui.chip, ui.chipTeal)}> ראשי</span>}</td>
                <td>{v.description}</td><td className="num">{v.durationSeconds}s</td>
                <td>{v.hasFile ? <Icon name="check" size={16} /> : 'עוד אין'}</td><td>{v.active ? 'כן' : 'לא'}</td></tr>)}</tbody>
            </table></div>
          </section>
        </>
      )}
    </Page>
  )
}
