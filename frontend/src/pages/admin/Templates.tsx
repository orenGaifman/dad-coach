import { Fragment, useState } from 'react'
import { useAdminTemplates, useApproveTemplate } from '../../lib/admin'
import { errorLine } from '../../lib/errors'
import type { AdminTemplateRow } from '../../lib/types'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import { useToast } from '../../shared/Toast'
import ui from '../../shared/ui.module.css'
import styles from './Admin.module.css'

/** Body with {{n}} placeholders highlighted, exactly as submitted to Meta. */
function RawBody({ body }: { body: string }) {
  const parts = body.split(/(\{\{\d+\}\})/g)
  return (
    <pre className={styles.tplRaw}>
      {parts.map((p, i) => (/^\{\{\d+\}\}$/.test(p) ? <span key={i} className={styles.tplVar}>{p}</span> : <Fragment key={i}>{p}</Fragment>))}
    </pre>
  )
}

function CopyButton({ text, label }: { text: string; label: string }) {
  const toast = useToast()
  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      toast('הועתק')
    } catch {
      toast('ההעתקה נכשלה - אפשר לסמן ולהעתיק ידנית', 'error')
    }
  }
  return (
    <button type="button" className={`${ui.btn} ${ui.ghost} ${ui.sm}`} onClick={copy}>{label}</button>
  )
}

function TemplateCard({ t }: { t: AdminTemplateRow }) {
  const approve = useApproveTemplate()
  const toast = useToast()
  const [checked, setChecked] = useState(false)
  const registeredOk = t.registeredStatus === 'APPROVED' && t.registeredBodyMatches

  async function markApproved() {
    try {
      await approve.mutateAsync(t.name)
      toast('נרשמה כמאושרת - הודעות מחוץ ל-24 השעות ישלחו בה')
    } catch (e) {
      toast(errorLine(e), 'error')
    }
  }

  return (
    <section className={ui.card} aria-label={t.name}>
      <div className={ui.cardHead}>
        <h2 className={ui.cardTitle}>התבנית הכללית</h2>
        <span className={styles.tplName}><bdi dir="ltr">{t.name}</bdi></span>
      </div>
      <div className={ui.row}>
        <span className={`${ui.chip} ${ui.chipTeal}`}>{t.category === 'UTILITY' ? 'Utility' : t.category}</span>
        <span className={ui.chip}>עברית (he)</span>
        <span className={`${ui.chip} ${registeredOk ? ui.chipSuccess : ui.chipWarning}`}>
          {registeredOk ? 'רשומה כמאושרת במסד' : t.registeredStatus ? `במסד: ${t.registeredStatus}` : 'עוד לא רשומה במסד'}
        </span>
        <span className={`${ui.chip} ${t.configured ? ui.chipSuccess : ui.chipWarning}`}>
          {t.configured ? 'מוגדרת בשרת' : 'לא מוגדרת בשרת'}
        </span>
      </div>
      {t.registeredStatus && !t.registeredBodyMatches && (
        <p className={styles.bad} role="note">הגוף שרשום במסד שונה מהגוף כאן - סימון כמאושרת יעדכן אותו לגוף שלמטה.</p>
      )}
      <div className={ui.grid2}>
        <div className={ui.stackSm}>
          <div className={styles.tplLabelRow}>
            <span className={ui.label}>הגוף להגשה ב-Meta</span>
            <CopyButton text={t.body} label="העתקה" />
          </div>
          <RawBody body={t.body} />
          <div className={styles.tplLabelRow}>
            <span className={ui.label}>דוגמה ל-{'{{1}}'} בטופס של Meta</span>
            <CopyButton text={t.example} label="העתקה" />
          </div>
          <pre className={styles.tplRaw}>{t.example}</pre>
        </div>
        <div className={ui.stackSm}>
          <span className={ui.label}>כך זה ייקרא אצל האבא</span>
          <div className={styles.tplScreen}>
            <div className={styles.tplBubble}>{t.sample}</div>
          </div>
        </div>
      </div>
      <p className={ui.hint}>
        {'{{1}}'} הוא ההודעה של המאמן, משוטחת לשורה אחת; שורת הזהות יורדת ממנה כי היא כבר בגוף. ההגשה נעשית ידנית
        ב-Meta Business Manager (קטגוריה Utility, שפה Hebrew). אחרי שמטא מאשרת: בשרת{' '}
        <bdi dir="ltr">WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME={t.name}</bdi>, וכאן - סימון כמאושרת.
      </p>
      {!registeredOk && (
        <div className={ui.stackSm}>
          <label className={ui.check}>
            <input type="checkbox" checked={checked} onChange={(e) => setChecked(e.target.checked)} />
            <span>Meta אישרה את התבנית הזו, בנוסח הזה בדיוק.</span>
          </label>
          <div>
            <button type="button" className={`${ui.btn} ${ui.primary}`} disabled={!checked || approve.isPending} onClick={markApproved}>
              {approve.isPending ? 'רושם…' : 'סימון כמאושרת במסד'}
            </button>
          </div>
        </div>
      )}
    </section>
  )
}

export function TemplatesPage() {
  const list = useAdminTemplates()
  return (
    <Page title="תבניות וואטסאפ"
          subtitle="כל הודעה יזומה לאבא שלא כתב 24 שעות יוצאת בתבנית שמטא אישרה. בלי תבנית מאושרת ורשומה - ההודעה לא נשלחת בכלל.">
      {list.isPending ? <Skeleton lines={6} /> : list.error ? <ErrorState error={list.error} onRetry={() => list.refetch()} />
        : !list.data || list.data.length === 0 ? <EmptyState title="אין תבניות בקטלוג" icon="message" />
        : <div className={ui.stack}>{list.data.map((t) => <TemplateCard key={t.name} t={t} />)}</div>}
      {list.data && list.data.some((t) => t.registeredStatus === 'APPROVED' && t.registeredBodyMatches && !t.configured) && (
        <p className={styles.bad} role="note">
          התבנית רשומה כמאושרת אבל השרת לא מוגדר לשלוח בה - חסר{' '}
          <bdi dir="ltr">WORKFLOW_PLATFORM_CALLBACK_TEMPLATE_NAME</bdi> ב-Render.
        </p>
      )}
    </Page>
  )
}
