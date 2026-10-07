import { useIntegrations } from '../../lib/admin'
import { dateTime, displayPhone } from '../../lib/format'
import { reasonLabel } from '../../lib/labels'
import { Page } from '../../shared/Page'
import { ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import styles from './Admin.module.css'
import { VoiceNotesCard } from './VoiceNotesCard'

function Yes({ ok, yes = 'מוגדר', no = 'לא מוגדר' }: { ok: boolean; yes?: string; no?: string }) {
  return <span className={ok ? styles.ok : styles.bad}>{ok ? yes : no}</span>
}

export function IntegrationsPage() {
  const i = useIntegrations()
  return (
    <Page title="אינטגרציות" subtitle="מה מחובר, ומתי עבר משהו בפעם האחרונה">
      {i.isPending ? <Skeleton lines={6} /> : i.error ? <ErrorState error={i.error} onRetry={() => i.refetch()} /> : (
        <div className={ui.grid2}>
          <section className={ui.card}>
            <div className={ui.cardHead}><h2 className={ui.cardTitle}>וואטסאפ</h2></div>
            <dl className={styles.kv}>
              <dt>Cloud API</dt><dd><Yes ok={i.data.whatsapp.configured} /></dd>
              <dt>מספר ציבורי (wa.me)</dt><dd className="ltr">{i.data.whatsapp.publicNumber ? displayPhone(i.data.whatsapp.publicNumber) : '—'}</dd>
              <dt>הודעה נכנסת אחרונה</dt><dd>{dateTime(i.data.whatsapp.lastInbound)}</dd>
              <dt>הודעה יוצאת אחרונה</dt><dd>{dateTime(i.data.whatsapp.lastOutbound)}</dd>
              <dt>כישלון אחרון</dt><dd>{i.data.whatsapp.lastFailure ? <>{reasonLabel(i.data.whatsapp.lastFailure.reason)} · {dateTime(i.data.whatsapp.lastFailure.at)}</> : '—'}</dd>
            </dl>
          </section>
          <section className={ui.card}>
            <div className={ui.cardHead}><h2 className={ui.cardTitle}>AI Workflow Platform</h2></div>
            <dl className={styles.kv}>
              <dt>מופעל</dt><dd><Yes ok={i.data.platform.enabled} yes="כן" no="לא" /></dd>
              <dt>עונה</dt><dd><Yes ok={i.data.platform.reachable} yes="כן" no="לא" /></dd>
              <dt>Callback להודעות מתוזמנות</dt><dd><Yes ok={i.data.platform.callbackEnabled} /></dd>
              <dt>תבנית מחוץ ל-24 שעות</dt><dd><Yes ok={i.data.platform.callbackTemplate} /></dd>
              <dt>פאנל ניהול (מפתח)</dt><dd><Yes ok={i.data.platform.adminPanelConfigured} /></dd>
            </dl>
          </section>
          <section className={ui.card}>
            <div className={ui.cardHead}><h2 className={ui.cardTitle}>עוד</h2></div>
            <dl className={styles.kv}>
              <dt>Google Calendar OAuth</dt><dd><Yes ok={i.data.calendarOAuthConfigured} /></dd>
              <dt>Ops API</dt><dd><Yes ok={i.data.opsApiConfigured} /></dd>
              <dt>סרטוני הדרכה</dt><dd><Yes ok={i.data.trainingMediaConfigured} /></dd>
              <dt>כתובת הדף האישי</dt><dd className="ltr">{i.data.webBaseUrl}</dd>
            </dl>
          </section>
          {i.data.voiceNotes && <VoiceNotesCard status={i.data.voiceNotes} />}
        </div>
      )}
    </Page>
  )
}
