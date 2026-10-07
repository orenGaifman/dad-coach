import { achievement } from '../../lib/achievements'
import { beltImage, beltName, toNextBelt } from '../../lib/belts'
import { useProgress } from '../../lib/father'
import { dayMonth, duration } from '../../lib/format'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Father.module.css'

const STATUS: Record<string, string> = { ACTIVE: 'השבוע', PENDING: 'השבוע', COMPLETED: 'הושלם', MISSED: 'לא הושלם', CANCELLED: 'בוטל' }

/** התקדמות: belts earned by sessions that really happened, achievements, and the weeks behind him. */
export function ProgressPage() {
  const progress = useProgress()
  if (progress.isPending) return <Page title="התקדמות"><Skeleton lines={4} /></Page>
  if (progress.error) return <Page title="התקדמות"><ErrorState error={progress.error} onRetry={() => progress.refetch()} /></Page>
  const v = progress.data
  const p = v.progress
  return (
    <Page title="התקדמות" subtitle="כל מפגש שקרה באמת מקדם אותך">
      <section className={ui.card} aria-labelledby="belts-title">
        <div className={ui.cardHead}>
          <div>
            <h2 id="belts-title" className={ui.cardTitle}>{beltName(p.belt)}</h2>
            <p className={ui.cardSub}>{p.totalCompleted} מפגשים שקרו · {toNextBelt(p.nextBelt, p.sessionsToNextBelt)}</p>
          </div>
          {p.streakWeeks > 0 && <span className={cx(ui.chip, ui.chipGlow)}><Icon name="flame" size={14} /> {p.streakWeeks} שבועות ברצף</span>}
        </div>
        <ol className={styles.ladder}>
          {v.belts.map((b) => (
            <li key={b.belt} className={cx(styles.rung, !b.reached && styles.rungLocked, b.current && styles.rungCurrent)}
                aria-current={b.current ? 'step' : undefined}>
              <img src={beltImage(b.belt)} alt="" loading="lazy" />
              <span>{beltName(b.belt).replace('חגורה ', '')}</span>
              <span className="num">{b.to == null ? `${b.from}+` : `${b.from}–${b.to}`}</span>
            </li>
          ))}
        </ol>
      </section>

      <section className={ui.card} aria-labelledby="ach-title">
        <div className={ui.cardHead}><h2 id="ach-title" className={ui.cardTitle}>הישגים</h2>
          <span className={ui.cardSub}>{v.achievements.filter((a) => a.earned).length} מתוך {v.achievements.length}</span></div>
        <div className={styles.badges}>
          {v.achievements.map((a) => {
            const t = achievement(a.key)
            return (
              <div key={a.key} className={cx(styles.badge, !a.earned && styles.badgeLocked)}>
                <img src={`/achievements/${a.image}.webp`} alt="" loading="lazy" />
                <strong>{t.title}</strong>
                <span className={cx(ui.xs, ui.muted)}>{a.earned ? t.text : <><Icon name="lock" size={12} /> {t.text}</>}</span>
              </div>
            )
          })}
        </div>
      </section>

      <section className={ui.card} aria-labelledby="weeks-title">
        <div className={ui.cardHead}><h2 id="weeks-title" className={ui.cardTitle}>השבועות שלך</h2></div>
        {v.weeks.length === 0 ? (
          <EmptyState title="עוד אין שבועות עם יעד" icon="chart">כתוב למאמן: <q>השבוע אני רוצה 3 שעות עם הילדים</q></EmptyState>
        ) : (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <caption className="visually-hidden">השבועות שלך</caption>
              <thead><tr><th>שבוע</th><th>יעד</th><th>עשית</th><th>מצב</th></tr></thead>
              <tbody>
                {v.weeks.map((w) => (
                  <tr key={w.weekStart}>
                    <td className="num">{dayMonth(w.weekStart)}</td>
                    <td>{duration(w.targetHours * 60)}</td>
                    <td>{duration(w.creditedMinutes)}</td>
                    <td>{w.met ? <span className={cx(ui.chip, ui.chipSuccess)}>עמדת ביעד</span>
                      : <span className={ui.chip}>{STATUS[w.status] ?? w.status}</span>}</td>
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
