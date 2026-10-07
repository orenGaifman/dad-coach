import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from './api'

const AREAS = /^\/(home|sessions|children|progress|settings|training|admin)(\/|$)/

/** A lost session anywhere sends him to sign in again, back to where he was. */
function onAuthLost(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    const { pathname, search } = window.location
    if (!pathname.startsWith('/login') && !pathname.startsWith('/auth')) {
      const next = AREAS.test(pathname) ? `?next=${encodeURIComponent(pathname + search)}` : ''
      window.location.assign(`/login${next}`)
    }
  }
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: onAuthLost }),
  mutationCache: new MutationCache({ onError: onAuthLost }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      retry: (failureCount, error) => {
        // Never retry what retrying cannot fix: auth, access, missing, invalid.
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
        return failureCount < 2
      },
    },
    mutations: { retry: false },
  },
})
