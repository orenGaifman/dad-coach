import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { Child, Home, ProgressView, SessionsView, Settings, TrainingLibrary } from './types'
import { useScreenOwner } from './viewAs'

/** His home - or, under view-as, the admin's read of exactly the same object. */
export function useHome() {
  const { homeApi } = useScreenOwner()
  return useQuery({ queryKey: ['home', homeApi], queryFn: () => api<Home>(homeApi) })
}

export const useSessions = () => useQuery({ queryKey: ['sessions'], queryFn: () => api<SessionsView>('/father/sessions') })
export const useChildren = () => useQuery({ queryKey: ['children'], queryFn: () => api<Child[]>('/father/children') })
export const useProgress = () => useQuery({ queryKey: ['progress'], queryFn: () => api<ProgressView>('/father/progress') })
export const useSettings = () => useQuery({ queryKey: ['settings'], queryFn: () => api<Settings>('/father/settings') })
export const useTraining = () => useQuery({ queryKey: ['training'], queryFn: () => api<TrainingLibrary>('/father/training') })

/** Every write to his week refreshes everything that shows the week. */
function useWeekRefresh() {
  const qc = useQueryClient()
  return () => Promise.all(['home', 'sessions', 'progress', 'children', 'settings', 'me'].map((k) => qc.invalidateQueries({ queryKey: [k] })))
}

export function useConfirmSession() {
  const refresh = useWeekRefresh()
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) =>
      api(`/father/sessions/${id}/confirm`, { method: 'POST', body: { note: note?.trim() || null } }),
    onSuccess: refresh,
  })
}

export function useCancelSession() {
  const refresh = useWeekRefresh()
  return useMutation({
    mutationFn: (id: string) => api(`/father/sessions/${id}/cancel`, { method: 'POST' }),
    onSuccess: refresh,
  })
}

export function useSaveChild() {
  const refresh = useWeekRefresh()
  return useMutation({
    mutationFn: ({ id, name, age }: { id?: number; name: string; age: number }) =>
      id ? api<Child>(`/father/children/${id}`, { method: 'PUT', body: { name, age } })
         : api<Child>('/father/children', { method: 'POST', body: { name, age } }),
    onSuccess: refresh,
  })
}

export function useSaveSettings() {
  const refresh = useWeekRefresh()
  return useMutation({
    mutationFn: (body: { name?: string; timezone?: string }) => api<Settings>('/father/settings', { method: 'PUT', body }),
    onSuccess: refresh,
  })
}
