import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useTraining } from '../../lib/father'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'

/** הדרכה: short videos (Big Boss D-171 pattern). Until the videos are published, a quiet "soon". */
export function TrainingPage() {
  const training = useTraining()
  const [playing, setPlaying] = useState<string | null>(null)
  const record = useMutation({
    mutationFn: ({ slug, completed }: { slug: string; completed: boolean }) =>
      api(`/father/training/${slug}/progress`, { method: 'POST', body: { completed } }),
  })
  if (training.isPending) return <Page title="הדרכה"><Skeleton lines={3} /></Page>
  if (training.error) return <Page title="הדרכה"><ErrorState error={training.error} onRetry={() => training.refetch()} /></Page>
  if (!training.data.available) {
    return (
      <Page title="הדרכה">
        <section className={ui.card}>
          <EmptyState title="בקרוב: סרטונים קצרים" icon="play">
            איך מתכננים שבוע עם המאמן, מה רואים בלוח, ואיך עולים בחגורות. עד אז - פשוט כתוב למאמן בוואטסאפ.
          </EmptyState>
        </section>
      </Page>
    )
  }
  return (
    <Page title="הדרכה" subtitle="סרטונים קצרים, דקה-שתיים כל אחד">
      <div className={ui.stack}>
        {training.data.videos.map((v) => (
          <section key={v.slug} className={ui.card}>
            <div className={ui.cardHead}>
              <h2 className={ui.cardTitle}>{v.title}</h2>
              {v.completed && <span className={cx(ui.chip, ui.chipSuccess)}>נצפה</span>}
            </div>
            <p className={ui.muted}>{v.description}</p>
            {playing === v.slug && v.videoUrl ? (
              <video src={v.videoUrl} poster={v.posterUrl ?? undefined} controls autoPlay playsInline style={{ inlineSize: '100%', borderRadius: 12, marginBlockStart: 12 }}
                     onEnded={() => record.mutate({ slug: v.slug, completed: true })} />
            ) : (
              <button type="button" className={cx(ui.btn, ui.secondary)} style={{ marginBlockStart: 12 }}
                      onClick={() => { setPlaying(v.slug); record.mutate({ slug: v.slug, completed: false }) }}>
                <Icon name="play" size={18} /> צפייה ({Math.max(1, Math.round(v.durationSeconds / 60))} דק׳)
              </button>
            )}
          </section>
        ))}
      </div>
    </Page>
  )
}
