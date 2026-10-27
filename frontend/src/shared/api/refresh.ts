import createClient from 'openapi-fetch'
import { useAuthStore } from './authStore'
import { DEFAULT_BASE_URL } from './baseUrl'
import type { paths } from './schema.gen'

/**
 * - `refreshed`: new access token is in the store.
 * - `expired`: the server ended the session; the store is cleared.
 * - `unavailable`: could not tell (network, 5xx, repeated race); the session is left as it is.
 */
export type RefreshOutcome = 'refreshed' | 'expired' | 'unavailable'

interface RefreshOptions {
  /** Wait before the single retry after `REFRESH_RACE`. */
  raceRetryDelayMs?: number
}

export const RACE_RETRY_DELAY_MS = 300

/** Problem codes of /auth/refresh that matter here (RFC 9457 `code`). */
const REFRESH_RACE = 'REFRESH_RACE'
const UNAUTHORIZED = 'UNAUTHORIZED'
const ACCOUNT_LOCKED = 'ACCOUNT_LOCKED'

function problemCode(error: unknown): string | undefined {
  if (typeof error !== 'object' || error === null) return undefined
  if ('code' in error && typeof error.code === 'string') return error.code
  // The current backend serializes ProblemDetail's stable code as `errorCode`.
  if ('errorCode' in error && typeof error.errorCode === 'string') return error.errorCode
  return undefined
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

async function callRefresh(baseUrl: string, raceRetryDelayMs: number): Promise<RefreshOutcome> {
  // A bare client: no auth middleware, otherwise a 401 here would try to refresh again.
  const client = createClient<paths>({
    baseUrl,
    credentials: 'include',
    fetch: (request) => globalThis.fetch(request),
  })
  for (let attempt = 0; attempt < 2; attempt++) {
    let result
    try {
      result = await client.POST('/api/v1/auth/refresh')
    } catch {
      return 'unavailable'
    }
    if (result.data) {
      useAuthStore.getState().setSession(result.data)
      return 'refreshed'
    }
    const code = problemCode(result.error)
    const { status } = result.response
    // Another tab or request won the rotation: its new cookie is already set, so try once more.
    if (status === 401 && code === REFRESH_RACE) {
      if (attempt === 0) {
        await sleep(raceRetryDelayMs)
        continue
      }
      return 'unavailable'
    }
    if ((status === 401 && code === UNAUTHORIZED) || (status === 403 && code === ACCOUNT_LOCKED)) {
      useAuthStore.getState().clear()
      return 'expired'
    }
    return 'unavailable'
  }
  return 'unavailable'
}

let inflight: Promise<RefreshOutcome> | null = null

/**
 * Rotates the refresh cookie. Callers that arrive while a refresh is running share it, so many
 * requests failing with 401 at the same time cause exactly one POST /auth/refresh (the server
 * revokes the whole token family on a reused cookie).
 */
export function refreshSession(
  baseUrl: string = DEFAULT_BASE_URL,
  { raceRetryDelayMs = RACE_RETRY_DELAY_MS }: RefreshOptions = {},
): Promise<RefreshOutcome> {
  inflight ??= callRefresh(baseUrl, raceRetryDelayMs).finally(() => {
    inflight = null
  })
  return inflight
}
