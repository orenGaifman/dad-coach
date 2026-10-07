import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { errorLine } from '../../lib/errors'
import { useSaveSettings, useSettings } from '../../lib/father'
import type { Settings } from '../../lib/types'
import { ConfirmDialog } from '../../shared/ConfirmDialog'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { ErrorState, FormError, Skeleton } from '../../shared/States'
import { useToast } from '../../shared/Toast'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'

const ZONES: { id: string; label: string }[] = [
  { id: 'Asia/Jerusalem', label: 'ישראל' },
  { id: 'Europe/London', label: 'לונדון' },
  { id: 'Europe/Berlin', label: 'מרכז אירופה' },
  { id: 'America/New_York', label: 'ניו יורק' },
  { id: 'America/Los_Angeles', label: 'לוס אנג׳לס' },
  { id: 'Australia/Sydney', label: 'סידני' },
]

/** הגדרות: his name, his timezone, Google Calendar (optional), signing out, deleting his data. */
export function SettingsPage() {
  const settings = useSettings()
  if (settings.isPending) return <Page title="הגדרות"><Skeleton lines={4} /></Page>
  if (settings.error) return <Page title="הגדרות"><ErrorState error={settings.error} onRetry={() => settings.refetch()} /></Page>
  return <SettingsForm s={settings.data} />
}

function SettingsForm({ s }: { s: Settings }) {
  const save = useSaveSettings()
  const toast = useToast()
  const [name, setName] = useState(s.name)
  const [zone, setZone] = useState(s.timezone)
  useEffect(() => { setName(s.name); setZone(s.timezone) }, [s.name, s.timezone])
  const dirty = name.trim() !== s.name || zone !== s.timezone
  const zones = ZONES.some((z) => z.id === s.timezone) ? ZONES : [{ id: s.timezone, label: s.timezone }, ...ZONES]

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('calendar_connected')) toast('היומן מחובר. מפגשים חדשים ייכנסו אליו.')
    if (params.get('calendar_error')) toast('החיבור ליומן לא הושלם. אפשר לנסות שוב.', 'error')
    if (params.has('calendar_connected') || params.has('calendar_error')) window.history.replaceState(null, '', '/settings')
  }, [toast])

  return (
    <Page title="הגדרות">
      <section className={ui.card} aria-labelledby="me-title">
        <div className={ui.cardHead}><h2 id="me-title" className={ui.cardTitle}>הפרטים שלך</h2></div>
        <div className={ui.formGrid}>
          <div className={ui.field}>
            <label className={ui.label} htmlFor="name">איך לקרוא לך</label>
            <input id="name" className={ui.input} value={name} maxLength={120} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className={ui.field}>
            <label className={ui.label} htmlFor="zone">אזור זמן</label>
            <select id="zone" className={ui.select} value={zone} onChange={(e) => setZone(e.target.value)}>
              {zones.map((z) => <option key={z.id} value={z.id}>{z.label}</option>)}
            </select>
            <span className={ui.hint}>לפיו השבוע מתחיל ביום ראשון והתזכורות מגיעות בזמן.</span>
          </div>
        </div>
        <FormError error={save.error} />
        <div className={ui.rowEnd} style={{ marginBlockStart: 16 }}>
          <button type="button" className={cx(ui.btn, ui.primary)} disabled={!dirty || !name.trim() || save.isPending}
                  onClick={async () => { try { await save.mutateAsync({ name: name.trim(), timezone: zone }); toast('נשמר.') } catch { /* shown */ } }}>
            {save.isPending ? 'שומר…' : 'שמירה'}
          </button>
        </div>
      </section>

      <CalendarCard s={s} />
      <AccountCard s={s} />
    </Page>
  )
}

function CalendarCard({ s }: { s: Settings }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  async function connect() {
    setBusy(true)
    try {
      const r = await api<{ url: string }>('/father/calendar/connect', { method: 'POST' })
      window.location.assign(r.url)
    } catch (e) {
      toast(errorLine(e), 'error')
      setBusy(false)
    }
  }
  async function disconnect() {
    setBusy(true)
    try {
      await api('/father/calendar/disconnect', { method: 'POST' })
      await qc.invalidateQueries({ queryKey: ['settings'] })
      await qc.invalidateQueries({ queryKey: ['home'] })
      toast('היומן נותק. המפגשים נשארים כאן ובוואטסאפ.')
    } catch (e) {
      toast(errorLine(e), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className={ui.card} aria-labelledby="cal-title">
      <div className={ui.cardHead}>
        <h2 id="cal-title" className={ui.cardTitle}>יומן Google</h2>
        {s.calendarConnected ? <span className={cx(ui.chip, ui.chipSuccess)}>מחובר</span> : <span className={ui.chip}>לא מחובר</span>}
      </div>
      <p className={ui.muted} style={{ marginBlockEnd: 16 }}>
        לא חובה. כשהיומן מחובר, המפגשים שאתה קובע עם המאמן נכנסים אליו, והמאמן רואה מתי אתה פנוי.
      </p>
      {s.calendarConnected ? (
        <button type="button" className={cx(ui.btn, ui.ghost)} disabled={busy} onClick={disconnect}>ניתוק היומן</button>
      ) : s.calendarAvailable ? (
        <button type="button" className={cx(ui.btn, ui.secondary)} disabled={busy} onClick={connect}>
          <Icon name="calendar" size={18} /> חיבור יומן Google
        </button>
      ) : (
        <p className={ui.note}><Icon name="info" size={18} /> החיבור ליומן עוד לא זמין. בינתיים הכול עובד גם בלעדיו.</p>
      )}
    </section>
  )
}

function AccountCard({ s }: { s: Settings }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [deleting, setDeleting] = useState(false)
  const [typed, setTyped] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  async function logout(all: boolean) {
    try {
      await api(all ? '/auth/logout-all' : '/auth/logout', { method: 'POST' })
      qc.clear()
      navigate('/login', { replace: true, state: { loggedOut: all ? 'all' : 'one' } })
    } catch (e) {
      toast(errorLine(e), 'error')
    }
  }
  async function deleteData() {
    setBusy(true)
    setError(null)
    try {
      await api('/father/delete-my-data', { method: 'POST', body: { confirmation: typed.trim() } })
      qc.clear()
      navigate('/login', { replace: true, state: { loggedOut: 'deleted' } })
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }
  return (
    <section className={ui.card} aria-labelledby="acc-title">
      <div className={ui.cardHead}><h2 id="acc-title" className={ui.cardTitle}>חשבון</h2></div>
      <div className={ui.row}>
        <button type="button" className={cx(ui.btn, ui.ghost)} onClick={() => logout(false)}><Icon name="logout" size={18} /> יציאה</button>
        <button type="button" className={cx(ui.btn, ui.ghost)} onClick={() => logout(true)}><Icon name="shield" size={18} /> יציאה מכל המכשירים</button>
      </div>
      <hr style={{ border: 0, borderBlockStart: '1px solid var(--dc-ink-100)', marginBlock: 20 }} />
      <div className={ui.stackSm}>
        <strong>מחיקת הנתונים שלי</strong>
        <p className={ui.muted}>מוחק את החשבון, הילדים, המפגשים, היעדים וההיסטוריה עם המאמן. אי אפשר לבטל את זה.</p>
        <button type="button" className={cx(ui.btn, ui.dangerGhost)} style={{ justifySelf: 'start' }} onClick={() => { setTyped(''); setError(null); setDeleting(true) }}>
          <Icon name="trash" size={18} /> מחיקת הנתונים שלי
        </button>
      </div>
      <ConfirmDialog open={deleting} title="למחוק את כל הנתונים שלך?" confirmLabel="מחיקה לצמיתות" danger busy={busy}
                     error={error} confirmDisabled={typed.trim() !== s.deleteConfirmation}
                     onClose={() => setDeleting(false)} onConfirm={deleteData}>
        <p>החשבון ייסגר מיד, והמאמן יפסיק לשלוח הודעות. תוך זמן קצר יימחקו גם השיחות שלך עם המאמן.</p>
        <div className={ui.field}>
          <label className={ui.label} htmlFor="del">כדי לאשר, כתוב <strong>{s.deleteConfirmation}</strong></label>
          <input id="del" className={ui.input} value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
        </div>
      </ConfirmDialog>
    </section>
  )
}
