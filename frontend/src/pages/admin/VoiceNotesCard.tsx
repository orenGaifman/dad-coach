import { useSetVoiceNotes } from '../../lib/admin'
import { errorLine } from '../../lib/errors'
import { dateTime } from '../../lib/format'
import type { VoiceNotesStatus } from '../../lib/types'
import { explainVoiceNoteError } from '../../lib/voiceNotes'
import { useToast } from '../../shared/Toast'
import ui from '../../shared/ui.module.css'
import styles from './Admin.module.css'

/**
 * D-027 (Big Boss D-176): the one setting on the integrations screen - whether the coach listens to WhatsApp voice
 * notes. On unless the team turned it off; whether an ElevenLabs key is set (never the key); the last note heard and
 * the last one that was not.
 */
export function VoiceNotesCard({ status }: { status: VoiceNotesStatus }) {
  const save = useSetVoiceNotes()
  const toast = useToast()

  async function toggle(enabled: boolean) {
    try {
      await save.mutateAsync(enabled)
      toast(enabled ? 'הודעות קוליות הופעלו' : 'הודעות קוליות כובו')
    } catch (e) {
      toast(errorLine(e), 'error')
    }
  }

  return (
    <section className={ui.card} aria-labelledby="voice-notes-title">
      <div className={ui.cardHead}><h2 className={ui.cardTitle} id="voice-notes-title">הודעות קוליות</h2></div>
      <label className={ui.switch}>
        <input type="checkbox" role="switch" checked={status.enabled} disabled={save.isPending}
               onChange={(e) => toggle(e.target.checked)} />
        <span>
          דאד קואץ׳ מקשיב להודעות קוליות בוואטסאפ
          <br />
          <span className={ui.hint}>
            ההקלטה מתומללת ב-ElevenLabs ונקראת כמו הודעה כתובה, והתשובה נפתחת במה שנשמע. כשכבוי, האב מתבקש לכתוב במילים.
          </span>
        </span>
      </label>
      {!status.configured && (
        <p className={styles.bad} role="note">
          אין מפתח ElevenLabs בשרת (<bdi dir="ltr">ELEVENLABS_API_KEY</bdi>) - עד שיוגדר, האבות מתבקשים לכתוב במילים גם כשזה מופעל.
        </p>
      )}
      <dl className={styles.kv}>
        <dt>מפתח ElevenLabs</dt><dd><span className={status.configured ? styles.ok : styles.bad}>{status.configured ? 'מוגדר' : 'לא מוגדר'}</span></dd>
        <dt>הודעה אחרונה שנשמעה</dt><dd>{dateTime(status.lastHeardAt)}</dd>
        <dt>תקלה אחרונה</dt>
        <dd>
          {status.lastError && status.lastFailedAt ? (
            <span title={status.lastError}>{explainVoiceNoteError(status.lastError)} · {dateTime(status.lastFailedAt)}</span>
          ) : '—'}
        </dd>
      </dl>
      <p className={ui.hint}>הזמנים נספרים מההפעלה האחרונה של השרת.</p>
    </section>
  )
}
