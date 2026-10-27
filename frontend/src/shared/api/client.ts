import createClient, { type Middleware } from 'openapi-fetch'
import { getAccessToken } from './authStore'
import { DEFAULT_BASE_URL } from './baseUrl'
import { refreshSession } from './refresh'
import type { paths } from './schema.gen'

const AUTH_PATH_PREFIX = '/api/v1/auth/'

function isAuthEndpoint(request: Request): boolean {
  return new URL(request.url).pathname.startsWith(AUTH_PATH_PREFIX)
}

/**
 * Adds the bearer token and, on a 401, refreshes the session once and replays the request once.
 * The auth endpoints are skipped: they are public and must not trigger a refresh themselves.
 */
function authMiddleware(baseUrl: string): Middleware {
  // Copies of in-flight requests: a sent Request has a spent body, so replay needs its own copy.
  const replayable = new Map<string, Request>()
  return {
    onRequest({ request, id }) {
      if (isAuthEndpoint(request)) return undefined
      const token = getAccessToken()
      if (token) request.headers.set('Authorization', `Bearer ${token}`)
      replayable.set(id, request.clone())
      return request
    },
    async onResponse({ request, response, id }) {
      const original = replayable.get(id)
      replayable.delete(id)
      if (response.status !== 401 || !original) return undefined

      const sentWith = request.headers.get('Authorization')
      const current = getAccessToken()
      // The token already changed while this request was in flight: just replay with the new one.
      const alreadyRotated = current !== null && sentWith !== `Bearer ${current}`
      if (!alreadyRotated && (await refreshSession(baseUrl)) !== 'refreshed') return undefined

      const token = getAccessToken()
      if (!token) return undefined
      original.headers.set('Authorization', `Bearer ${token}`)
      return globalThis.fetch(original)
    },
    onError({ id }) {
      replayable.delete(id)
      return undefined
    },
  }
}

export function createApiClient(baseUrl: string = DEFAULT_BASE_URL) {
  // credentials: 'include' lets the HttpOnly refresh cookie travel on a split deployment.
  const client = createClient<paths>({
    baseUrl,
    credentials: 'include',
    fetch: (request) => globalThis.fetch(request),
  })
  client.use(authMiddleware(baseUrl))
  return client
}

export const apiClient = createApiClient()
