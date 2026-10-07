import { Link } from 'react-router'
import { beltImage, beltName, toNextBelt } from '../../lib/belts'
import { useHome } from '../../lib/father'
import { dayLabel, daysLeft, duration, percent, weekRange } from '../../lib/format'
import type { Home } from '../../lib/types'
import { useScreenOwner } from '../../lib/viewAs'
import { bookingPrompt, coachLink } from '../../lib/whatsapp'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import { SessionRow } from './SessionParts'
import styles from './Father.module.css'

/**
 * בית / השבוע שלי. Everything here is the weekly plan the coach sees (GET /api/father/home). Under view-as the same
 * component renders the admin's read of it, with every action disabled.
 */
export function FatherHome() {
  const home = useHome()
  const { readOnly } = useScreenOwner()
  if (home.isPending) return <Page title="השבוע שלי"><Skeleton lines={4} /><Skeleton lines={3} /></Page>
  if (home.error) return <Page title="השבוע שלי"><ErrorState error={home.error} onRetry={() => home.refetch()} /></Page>
  const h = home.data
  const firstChild = h.children[0]?.name
  return (
    <Page title={h.name ? `שלום, ${h.name}` : 'השבוע שלי'} docTitle="השבוע שלי"
          subtitle={<>השבוע שלך · <span className="num">{weekRange(h.week.start, h.week.end)}</span> · {daysLeft(h.week.daysLeft)}</>}>
      <GoalCard h={h} firstChild={firstChild} />

      {h.awaitingConfirmation.length > 0 && (
        <section className={cx(ui.card, styles.awaiting)} aria-labelledby="awaiting-title">
          <div className={ui.cardHead}>
            <h2 id="awaiting-title" className={ui.cardTitle}>מחכה לאישור שלך</h2>
            <span className={ui.cardSub}>היה? סמן, וזה נספר לשבוע</span>
          </div>
          <ul className={styles.sessions}>
            {h.awaitingConfirmation.map((s) => <SessionRow key={s.id} session={s} today={h.today} />)}
          </ul>
        </section>
      )}

      <div className={ui.grid2}>
        <section className={ui.card} aria-labelledby="next-title">
          <div className={ui.cardHead}><h2 id="next-title" className={ui.cardTitle}>המפגש הבא</h2></div>
          {h.nextSession ? (
            <div className={styles.next}>
              <span className={styles.nextIcon}><Icon name="calendar" size={22} /></span>
              <div>
                <strong className={styles.nextWho}>{h.nextSession.childName}</strong>
                <div className={styles.nextWhen}>
                  {dayLabel(h.nextSession.localDate, h.nextSession.weekday, h.today)} · <span className="num">{h.nextSession.localStart}–{h.nextSession.localEnd}</span>
                </div>
                <div className={ui.muted}>{duration(h.nextSession.durationMinutes)} · נזכיר לך בבוקר ושעה לפני</div>
              </div>
            </div>
          ) : (
            <EmptyHint text="אין מפגש קרוב בלוח." prompt={bookingPrompt(firstChild)} />
          )}
        </section>

        <BeltCard h={h} />
      </div>

      <section className={ui.card} aria-labelledby="week-title">
        <div className={ui.cardHead}>
          <h2 id="week-title" className={ui.cardTitle}>המפגשים של השבוע</h2>
          <Link to="/sessions" className={cx(ui.btn, ui.ghost, ui.sm)} aria-disabled={readOnly} tabIndex={readOnly ? -1 : undefined}
                style={readOnly ? { opacity: 0.55, pointerEvents: 'none' } : undefined}
                onClick={(e) => { if (readOnly) e.preventDefault() }}>כל המפגשים</Link>
        </div>
        {h.sessionsThisWeek.length > 0 ? (
          <ul className={styles.sessions}>
            {h.sessionsThisWeek.map((s) => <SessionRow key={s.id} session={s} today={h.today} actions={false} />)}
          </ul>
        ) : (
          <EmptyHint text="עוד אין מפגשים השבוע." prompt={bookingPrompt(firstChild)} />
        )}
      </section>

      <CoachCard number={h.coachWhatsApp} />
    </Page>
  )
}

function GoalCard({ h, firstChild }: { h: Home; firstChild?: string }) {
  if (!h.goal.exists || !h.coverage.targetMinutes) {
    return (
      <section className={cx(ui.card, styles.goal)} aria-labelledby="goal-title">
        <div className={styles.goalEmpty}>
          <img src="/img/coach-greeting.webp" alt="" className={styles.goalArt} />
          <div className={ui.stackSm}>
            <h2 id="goal-title" className={ui.cardTitle}>עוד לא קבעת יעד לשבוע</h2>
            <p>כמה זמן אתה רוצה לתת לילדים השבוע? המאמן יעזור לך לחלק את זה למפגשים.</p>
            <Prompt text="השבוע אני רוצה 3 שעות עם הילדים" />
            {h.coverage.completedMinutes > 0 && <p className={ui.muted}>כבר השבוע: {duration(h.coverage.completedMinutes)} עם {firstChild ? 'הילדים' : 'הילד'}.</p>}
          </div>
        </div>
      </section>
    )
  }
  const target = h.coverage.targetMinutes
  const done = h.coverage.completedMinutes
  const planned = h.coverage.plannedMinutes
  const donePct = percent(done, target)
  const plannedPct = Math.min(100 - donePct, percent(planned, target))
  const missing = h.coverage.uncoveredMinutes ?? 0
  return (
    <section className={cx(ui.card, styles.goal)} aria-labelledby="goal-title">
      <div className={styles.goalHead}>
        <div>
          <h2 id="goal-title" className={styles.goalLabel}>היעד שלך השבוע</h2>
          <p className={styles.goalNumbers}>
            <span className={styles.goalDone}>{duration(done)}</span>
            <span className={styles.goalOf}> מתוך {duration(target)}</span>
          </p>
        </div>
        {done >= target && <span className={cx(ui.chip, ui.chipSuccess)}><Icon name="check" size={14} /> עמדת ביעד</span>}
      </div>
      <div className={styles.bar} role="img" aria-label={`עשית ${duration(done)}, מתוכנן ${duration(planned)}, מתוך ${duration(target)}`}>
        <span className={styles.barDone} style={{ inlineSize: `${donePct}%` }} />
        <span className={styles.barPlanned} style={{ inlineSize: `${plannedPct}%` }} />
      </div>
      <ul className={styles.legend}>
        <li><span className={styles.keyDone} />כבר עשית: {duration(done)}</li>
        <li><span className={styles.keyPlanned} />מתוכנן: {duration(planned)}</li>
        {h.coverage.awaitingMinutes > 0 && <li><span className={styles.keyAwaiting} />מחכה לאישור: {duration(h.coverage.awaitingMinutes)}</li>}
      </ul>
      <p className={styles.goalLine}>
        {done >= target ? 'סגרת את השבוע. כל מפגש נוסף הוא בונוס לילדים.'
          : missing > 0 ? <>חסרות עוד <strong>{duration(missing)}</strong> בלוח כדי לסגור את היעד. כתוב למאמן מתי, והוא יקבע.</>
          : 'השבוע מכוסה. נשאר רק להגיע למפגשים.'}
      </p>
    </section>
  )
}

function BeltCard({ h }: { h: Home }) {
  const p = h.progress
  return (
    <section className={cx(ui.card, styles.belt)} aria-labelledby="belt-title">
      <img src={beltImage(p.belt)} alt="" className={styles.beltArt} />
      <div className={styles.beltBody}>
        <h2 id="belt-title" className={ui.cardTitle}>{beltName(p.belt)}</h2>
        <div className={styles.beltBar} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={p.percentToNextBelt}
             aria-label="ההתקדמות לחגורה הבאה">
          <span style={{ inlineSize: `${p.percentToNextBelt}%` }} />
        </div>
        <p className={ui.small}>{toNextBelt(p.nextBelt, p.sessionsToNextBelt)}</p>
        <p className={cx(ui.small, ui.muted)}>
          {p.totalCompleted} מפגשים שקרו{p.streakWeeks > 0 ? <> · <Icon name="flame" size={14} /> {p.streakWeeks === 1 ? 'שבוע אחד ברצף' : `${p.streakWeeks} שבועות ברצף`}</> : null}
        </p>
      </div>
    </section>
  )
}

function EmptyHint({ text, prompt }: { text: string; prompt: string }) {
  return (
    <div className={styles.emptyHint}>
      <p>{text}</p>
      <Prompt text={prompt} />
    </div>
  )
}

/** "כתוב למאמן: '…'" - the exact words to send in WhatsApp. */
function Prompt({ text }: { text: string }) {
  return (
    <p className={styles.prompt}>
      <Icon name="message" size={16} />
      <span>כתוב למאמן: <q>{text}</q></span>
    </p>
  )
}

function CoachCard({ number }: { number: string | null }) {
  const { readOnly } = useScreenOwner()
  const link = coachLink(number, 'היי, רוצה לתכנן את השבוע עם הילדים')
  if (!link) return null
  return (
    <section className={cx(ui.card, styles.coach)}>
      <img src="/img/coach-thinking.webp" alt="" className={styles.coachArt} />
      <div className={styles.coachBody}>
        <h2 className={ui.cardTitle}>המאמן שלך בוואטסאפ</h2>
        <p>קובעים, מזיזים ומבטלים מפגשים בשיחה רגילה. הוא גם מזכיר, ושואל איך היה.</p>
        {readOnly ? (
          <button type="button" className={cx(ui.btn, ui.whatsapp)} disabled>
            <Icon name="whatsapp" size={20} /> דבר עם המאמן בוואטסאפ
          </button>
        ) : (
          <a className={cx(ui.btn, ui.whatsapp)} href={link} target="_blank" rel="noreferrer">
            <Icon name="whatsapp" size={20} /> דבר עם המאמן בוואטסאפ
          </a>
        )}
      </div>
    </section>
  )
}
