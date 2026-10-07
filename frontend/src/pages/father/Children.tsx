import { useState } from 'react'
import { useChildren, useSaveChild } from '../../lib/father'
import { ageLabel } from '../../lib/format'
import type { Child } from '../../lib/types'
import { Drawer } from '../../shared/Drawer'
import { Icon } from '../../shared/Icon'
import { Page } from '../../shared/Page'
import { EmptyState, ErrorState, FormError, Skeleton } from '../../shared/States'
import { useToast } from '../../shared/Toast'
import ui from '../../shared/ui.module.css'
import { cx } from '../../shared/cx'
import styles from './Father.module.css'

/** ילדים: who he spends the time with. The coach uses the age to suggest ideas that fit. */
export function Children() {
  const children = useChildren()
  const [editing, setEditing] = useState<Child | 'new' | null>(null)
  const add = <button type="button" className={cx(ui.btn, ui.primary)} onClick={() => setEditing('new')}><Icon name="plus" size={18} /> הוספת ילד</button>
  return (
    <Page title="ילדים" subtitle="המאמן משתמש בגיל כדי להציע רעיונות שמתאימים לכל אחד" actions={add}>
      {children.isPending ? <Skeleton lines={3} />
        : children.error ? <ErrorState error={children.error} onRetry={() => children.refetch()} />
        : children.data.length === 0 ? (
          <section className={ui.card}>
            <EmptyState title="עוד לא הוספת ילדים" icon="users" action={add}>
              אפשר להוסיף כאן, או לכתוב למאמן: <q>יש לי בת, נועה, בת 7</q>
            </EmptyState>
          </section>
        ) : (
          <div className={styles.kids}>
            {children.data.map((c) => (
              <section key={c.id} className={cx(ui.card, styles.kid)}>
                <span className={styles.kidAvatar} aria-hidden="true">{c.name.charAt(0)}</span>
                <div className={styles.kidBody}>
                  <strong>{c.name}</strong>
                  <span className={ui.muted}>{ageLabel(c.age)}</span>
                </div>
                <button type="button" className={ui.iconBtn} aria-label={`עריכת ${c.name}`} onClick={() => setEditing(c)}>
                  <Icon name="edit" size={18} />
                </button>
              </section>
            ))}
          </div>
        )}
      {editing && <ChildDrawer child={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </Page>
  )
}

function ChildDrawer({ child, onClose }: { child: Child | null; onClose: () => void }) {
  const save = useSaveChild()
  const toast = useToast()
  const [name, setName] = useState(child?.name ?? '')
  const [age, setAge] = useState(child ? String(child.age) : '')
  const ageNum = Number(age)
  const valid = name.trim().length > 0 && age !== '' && Number.isInteger(ageNum) && ageNum >= 0 && ageNum <= 25
  async function submit() {
    try {
      await save.mutateAsync({ id: child?.id, name: name.trim(), age: ageNum })
      toast(child ? 'נשמר.' : `${name.trim()} ברשימה.`)
      onClose()
    } catch {
      // shown below
    }
  }
  return (
    <Drawer open title={child ? `עריכת ${child.name}` : 'הוספת ילד'} onClose={onClose}
            footer={<>
              <button type="button" className={cx(ui.btn, ui.ghost)} onClick={onClose}>ביטול</button>
              <button type="button" className={cx(ui.btn, ui.primary)} disabled={!valid || save.isPending} onClick={submit}>
                {save.isPending ? 'שומר…' : 'שמירה'}
              </button>
            </>}>
      <div className={ui.field}>
        <label className={ui.label} htmlFor="child-name">שם</label>
        <input id="child-name" className={ui.input} value={name} maxLength={100} onChange={(e) => setName(e.target.value)} autoFocus />
      </div>
      <div className={ui.field}>
        <label className={ui.label} htmlFor="child-age">גיל</label>
        <input id="child-age" className={ui.input} type="number" inputMode="numeric" min={0} max={25} value={age}
               onChange={(e) => setAge(e.target.value)} style={{ maxInlineSize: 120 }} />
        <span className={ui.hint}>בשנים שלמות. מתחת לשנה - 0.</span>
      </div>
      <FormError error={save.error} />
    </Drawer>
  )
}
