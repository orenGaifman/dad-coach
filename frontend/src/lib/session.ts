import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { Me } from './types'

export function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: () => api<Me>('/me'), staleTime: 5 * 60_000 })
}

export const can = (me: Me | undefined, capability: string) => !!me?.capabilities.includes(capability)

/** Where a signed-in person lands: the team on the admin, a father on his week. */
export function homeFor(me: Me): string {
  return can(me, 'admin') ? '/admin' : can(me, 'father') ? '/home' : '/login'
}

/** A `next` from a link is honoured only inside the dashboard areas. */
export function safeNext(next: string | null | undefined): string | null {
  if (!next || next.startsWith('//')) return null
  return /^\/(home|sessions|children|progress|settings|training|admin)(\/[A-Za-z0-9_\-/]*)?$/.test(next) ? next : null
}
