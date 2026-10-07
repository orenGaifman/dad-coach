import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { AdminTraining, DeliveryRow, FatherDetail, FatherRow, Integrations, Overview, VoiceNotesStatus } from './types'

export const useOverview = () => useQuery({ queryKey: ['admin', 'overview'], queryFn: () => api<Overview>('/admin/overview') })
export const useFathers = (q: string, status: string) => useQuery({
  queryKey: ['admin', 'fathers', q, status],
  queryFn: () => api<FatherRow[]>(`/admin/fathers?q=${encodeURIComponent(q)}&status=${encodeURIComponent(status)}`),
})
export const useFatherDetail = (id: string) => useQuery({ queryKey: ['admin', 'father', id], queryFn: () => api<FatherDetail>(`/admin/fathers/${id}`) })
export const useIntegrations = () => useQuery({ queryKey: ['admin', 'integrations'], queryFn: () => api<Integrations>('/admin/integrations') })
export const useUndelivered = () => useQuery({ queryKey: ['admin', 'undelivered'], queryFn: () => api<DeliveryRow[]>('/admin/undelivered?days=7') })
export const useDeletions = () => useQuery({
  queryKey: ['admin', 'deletions'],
  queryFn: () => api<{ pending: number; rows: { fatherId: number; requestedAt: string; attempts: number; nextAttemptAt: string; lastError: string | null; purgeLocal: boolean }[]; deactivated: FatherRow[] }>('/admin/deletions'),
})
export const useAdminTraining = () => useQuery({ queryKey: ['admin', 'training'], queryFn: () => api<AdminTraining>('/admin/training') })

/** D-027: voice notes on or off for every father; the integrations screen shows the answer at once. */
export function useSetVoiceNotes() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (enabled: boolean) => api<VoiceNotesStatus>('/admin/integrations/voice-notes', { method: 'PUT', body: { enabled } }),
    onSuccess: (voiceNotes) => {
      qc.setQueryData<Integrations>(['admin', 'integrations'], (old) => (old ? { ...old, voiceNotes } : old))
    },
  })
}
