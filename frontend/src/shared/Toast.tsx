import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { Icon } from './Icon'
import styles from './Toast.module.css'
import { cx } from './cx'

type Tone = 'success' | 'error'
interface ToastItem { id: number; text: string; tone: Tone }
const ToastContext = createContext<(text: string, tone?: Tone) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const next = useRef(1)
  const show = useCallback((text: string, tone: Tone = 'success') => {
    const id = next.current++
    setItems((all) => [...all.slice(-2), { id, text, tone }])
    window.setTimeout(() => setItems((all) => all.filter((t) => t.id !== id)), tone === 'error' ? 7000 : 4000)
  }, [])
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className={styles.region} role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={cx(styles.toast, t.tone === 'error' && styles.error)}>
            <Icon name={t.tone === 'error' ? 'alert' : 'check'} size={18} />
            <span>{t.text}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  return useContext(ToastContext)
}
