import { createContext, useContext } from 'react'

/**
 * View-as (Big Boss D-105): whose screen this is. null on the father's own dashboard; set when the team looks at a
 * father's home from the admin - the SAME component renders, read-only, from the admin endpoint.
 */
export interface ViewAs {
  fatherId: number
  name: string
}

export const ViewAsContext = createContext<ViewAs | null>(null)

export function useViewAs(): ViewAs | null {
  return useContext(ViewAsContext)
}

/** "Is this screen mine to act on?" - checked against the screen's owner, never the viewer. */
export function useScreenOwner() {
  const viewAs = useViewAs()
  return {
    readOnly: viewAs != null,
    /** The home's API path: his own, or the admin's view of his. */
    homeApi: viewAs ? `/admin/fathers/${viewAs.fatherId}/home` : '/father/home',
  }
}
