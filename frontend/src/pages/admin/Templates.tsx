import { Fragment } from 'react'
import { useAdminTemplates, useSyncTemplates } from '../../lib/admin'
import { errorLine } from '../../lib/errors'
import { dateTime } from '../../lib/format'
import type { AdminTemplateRow } from '../../lib/types'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import { useToast } from '../../shared/Toast'
import ui from '../../shared/ui.module.css'
import styles from './Admin.module.css'

const META_STATUS: Record<string, { label: string; tone: string }> = {
  APPROVED: { label: 'מאושרת ב-Meta', tone: ui.chipSuccess },
  PENDING: { label: 'ממתינה לבדיקה ב-Meta', tone: ui.chipWarning },
  REJECTED: { label: 'נדחתה ב-Meta', tone: ui.chipDanger },
  PAUSED: { label: 'מושהית ב-Meta', tone: ui.chipDanger },
  DISABLED: { label: 'מושבתת ב-Meta', tone: ui.chipDanger },
  MISSING: { label: 'לא הוגשה ל-Meta', tone: ui.chipDanger },
  UNKNOWN: { label: 'עוד לא נקרא מהפלטפורמה', tone: '' },
}

const CATEGORY: Record<string, string> = { UTILITY: 'שירות (Utility)', MARKETING: 'שיווק (Marketing)', AUTHENTICATION: 'אימות' }

/** Body with {{n}} placeholders highlighted, exactly as submitted to Meta. */
function RawBody({ body }: { body: string }) {
  const parts = body.split(/(\{\{\d+\}\})/g)
  return (
    <pre className={styles.tplRaw}>
      {parts.map((p, i) => (/^\{\{\d+\}\}$/.test(p) ? <span key={i} className={styles.tplVar}>{p}</span> : <Fragment key={i}>{p}</Fragment>))}
    </pre>
  )
}

function CopyButton({ text }: { text: string }) {
  const toast = useToast()
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      toast('הועתק')
    } catch {
      toast('ההעתקה נכשלה - אפשר לסמן ולהעתיק ידנית', 'error')
    }
  }
  return <button type="button" className={`${ui.btn} ${ui.ghost} ${ui.sm}`} onClick={copy}>העתקה</button>
}

/** One line on what stands between this template and a message going out (the platform sends and picks the template). */
function readiness(t: AdminTemplateRow): string {
  if (t.ready) return 'הפלטפורמה שולחת בה עכשיו הודעות של דאד קואץ׳ מחוץ ל-24 השעות.'
  if (t.metaStatus === 'APPROVED' && !t.inUseAs) return 'מאושרת, אבל אף סוג הודעה לא ממופה אליה בפלטפורמה.'
  if (t.metaStatus === 'APPROVED') return 'מאושרת - הפלטפורמה תשתמש בה כשתסונכרן מול Meta.'
  if (t.metaStatus === 'PENDING') return t.general
    ? 'הוגשה וממתינה. עד האישור, הודעות מחוץ ל-24 השעות לא נשלחות.'
    : 'הוגשה וממתינה. עד האישור, ההודעה הזו יוצאת בתבנית הכללית (אם היא מאושרת).'
  if (t.metaStatus === 'REJECTED') return 'נדחתה. צריך לתקן ולהגיש מחדש.'
  if (t.metaStatus === 'MISSING') return 'לא קיימת ב-Meta. צריך להגיש אותה.'
  return 'עוד לא נקרא מצב מ-Meta.'
}

function TemplateCard({ t }: { t: AdminTemplateRow }) {
  const status = META_STATUS[t.metaStatus] ?? { label: t.metaStatus, tone: '' }
  return (
    <section className={ui.card} aria-label={t.name}>
      <div className={ui.cardHead}>
        <h2 className={ui.cardTitle}>{t.purpose}</h2>
        <span className={styles.tplName}><bdi dir="ltr">{t.name}</bdi></span>
      </div>
      <div className={ui.row}>
        <span className={`${ui.chip} ${status.tone}`}>{status.label}</span>
        <span className={`${ui.chip} ${t.ready ? ui.chipSuccess : ''}`}>{t.ready ? 'בשימוש בשליחה' : 'לא בשימוש עדיין'}</span>
        {t.metaCategory && <span className={`${ui.chip} ${ui.chipTeal}`}>{CATEGORY[t.metaCategory] ?? t.metaCategory}</span>}
        {t.metaPreviousCategory && t.metaPreviousCategory !== t.metaCategory && (
          <span className={`${ui.chip} ${ui.chipWarning}`}>Meta העבירה מ-{CATEGORY[t.metaPreviousCategory] ?? t.metaPreviousCategory}</span>
        )}
      </div>
      <p className={t.ready ? ui.hint : styles.bad} role="note">{readiness(t)}</p>
      {t.metaRejectedReason && <p className={styles.bad}>סיבת הדחייה ב-Meta: <bdi dir="ltr">{t.metaRejectedReason}</bdi></p>}
      <div className={ui.grid2}>
        <div className={ui.stackSm}>
          <div className={styles.tplLabelRow}>
            <span className={ui.label}>הגוף כפי שהוגש ל-Meta</span>
            <CopyButton text={t.body} />
          </div>
          <RawBody body={t.body} />
          {t.examples.map((example, i) => (
            <Fragment key={i}>
              <div className={styles.tplLabelRow}>
                <span className={ui.label}>דוגמה ל-<bdi dir="ltr">{`{{${i + 1}}}`}</bdi></span>
                <CopyButton text={example} />
              </div>
              <pre className={styles.tplRaw}>{example}</pre>
            </Fragment>
          ))}
          {t.quickReplies.length > 0 && (
            <>
              <span className={ui.label}>כפתורי תשובה מהירה, לפי הסדר</span>
              <div className={ui.row}>{t.quickReplies.map((b) => <span key={b} className={ui.chip}>{b}</span>)}</div>
            </>
          )}
          {t.urlButton && t.urlButtonUrl && (
            <>
              <div className={styles.tplLabelRow}>
                <span className={ui.label}>כפתור קישור (<bdi dir="ltr">{'{{1}}'}</bdi> = הקישור האישי)</span>
                <CopyButton text={t.urlButtonUrl} />
              </div>
              <div className={ui.row}><span className={ui.chip}>{t.urlButton} ↗</span></div>
              <pre className={styles.tplRaw}><bdi dir="ltr">{t.urlButtonUrl}</bdi></pre>
            </>
          )}
          {t.definition && (
            <div className={styles.tplLabelRow}>
              <span className={ui.label}>ההגדרה ל-Meta (JSON)</span>
              <CopyButton text={JSON.stringify(t.definition, null, 2)} />
            </div>
          )}
        </div>
        <div className={ui.stackSm}>
          <span className={ui.label}>כך זה ייקרא אצל האבא</span>
          <div className={styles.tplScreen}>
            <div className={styles.tplBubble}>{t.sample}</div>
            {t.quickReplies.map((b) => <div key={b} className={styles.tplButton}>{b}</div>)}
            {t.urlButton && <div className={styles.tplButton}>{t.urlButton} ↗</div>}
          </div>
        </div>
      </div>
    </section>
  )
}

export function TemplatesPage() {
  const list = useAdminTemplates()
  const sync = useSyncTemplates()
  const toast = useToast()

  async function syncNow() {
    try {
      await sync.mutateAsync()
      toast('המצב עודכן מהפלטפורמה')
    } catch (e) {
      toast(errorLine(e), 'error')
    }
  }

  const data = list.data
  const ready = data?.templates.filter((t) => t.ready).length ?? 0
  return (
    <Page title="תבניות וואטסאפ"
          subtitle="המצב של כל תבנית כפי שהפלטפורמה רואה אותו ב-Meta, ומה מהן כבר בשימוש. הפלטפורמה שולחת את ההודעות ובוחרת את התבנית לפי המיפוי שלה."
          actions={<button type="button" className={`${ui.btn} ${ui.secondary}`} disabled={sync.isPending || !data?.metaConfigured} onClick={syncNow}>
            {sync.isPending ? 'מסנכרן…' : 'סנכרון עכשיו'}
          </button>}>
      {list.isPending ? <Skeleton lines={6} /> : list.error ? <ErrorState error={list.error} onRetry={() => list.refetch()} />
        : !data || data.templates.length === 0 ? <EmptyState title="אין תבניות בקטלוג" icon="message" />
        : (
          <div className={ui.stack}>
            <section className={ui.card} aria-label="מצב הסנכרון">
              <dl className={styles.kv}>
                <dt>חשבון WhatsApp Business</dt><dd className="ltr">{data.wabaId || '—'}</dd>
                <dt>עודכן לאחרונה מהפלטפורמה</dt><dd>{data.refreshedAt ? dateTime(data.refreshedAt) : 'עוד לא'}</dd>
                <dt>בשימוש בשליחה</dt><dd className="num">{ready} מתוך {data.templates.length}</dd>
              </dl>
              {!data.metaConfigured && <p className={styles.bad} role="note">אין חיבור לפלטפורמה בשרת (<bdi dir="ltr">WORKFLOW_PLATFORM_ENABLED / WORKFLOW_PLATFORM_BASE_URL</bdi>).</p>}
              {data.lastError && <p className={styles.bad} role="note">הקריאה האחרונה מהפלטפורמה נכשלה: <bdi dir="ltr">{data.lastError}</bdi></p>}
              <p className={ui.hint}>המצב נקרא מהפלטפורמה (עד 5 דקות). הגשה, עריכה ומחיקה של תבניות נעשות ב-Meta, והמיפוי בין סוגי ההודעות לתבניות בפלטפורמה.</p>
            </section>
            {data.templates.map((t) => <TemplateCard key={t.name} t={t} />)}
          </div>
        )}
    </Page>
  )
}
