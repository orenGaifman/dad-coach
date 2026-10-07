const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** Every failure from the backend, typed: the Hebrew text is chosen by `code`, never the English `message`. */
export class ApiError extends Error {
  status: number
  code: string
  correlationId?: string
  details: { field: string; problem: string }[]

  constructor(status: number, code: string, message: string, correlationId?: string,
              details: { field: string; problem: string }[] = []) {
    super(message)
    this.status = status
    this.code = code
    this.correlationId = correlationId
    this.details = details
  }
}

function readCookie(name: string): string | undefined {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : undefined
}

export interface ApiOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/**
 * The one transport every query and mutation uses. Paths are relative to /api. Cookies always go
 * (session + CSRF are cookie based); non-GET calls echo the DADCOACH_XSRF cookie as X-XSRF-TOKEN
 * (double submit, D-005).
 */
export async function api<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers = new Headers({ Accept: 'application/json' })
  if (!SAFE_METHODS.has(method)) {
    const xsrf = readCookie('DADCOACH_XSRF')
    if (xsrf) headers.set('X-XSRF-TOKEN', xsrf)
  }
  let body: string | undefined
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(options.body)
  }

  let response: Response
  try {
    response = await fetch(`/api${path}`, { method, headers, body, credentials: 'include', signal: options.signal })
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK', 'network error')
  }

  if (response.status === 204) return undefined as T

  if (!response.ok) {
    let code = response.status === 401 ? 'UNAUTHENTICATED' : 'UNKNOWN_ERROR'
    let message = response.statusText
    let correlationId = response.headers.get('X-Correlation-Id') ?? undefined
    let details: { field: string; problem: string }[] = []
    try {
      const payload = await response.json()
      code = payload.code ?? code
      message = payload.message ?? message
      correlationId = payload.correlationId ?? correlationId
      details = Array.isArray(payload.details) ? payload.details : []
    } catch {
      // A non-JSON body (proxy page, CSRF refusal): keep the status-derived code.
      if (response.status === 403) code = 'FORBIDDEN'
    }
    throw new ApiError(response.status, code, message, correlationId, details)
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError
}
