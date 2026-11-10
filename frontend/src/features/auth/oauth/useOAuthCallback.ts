import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getAccessToken, useAuthStore } from '@/shared/api/authStore'
import { apiClient } from '@/shared/api/client'
import { refreshSession } from '@/shared/api/refresh'
import type { components } from '@/shared/api/schema.gen'

type OAuthProviderSlug = components['schemas']['OAuthProviderSlug']

export type CallbackState =
  { status: 'loading' } | { status: 'linking' } | { status: 'error'; code: string }

function isSupportedProvider(value: string): value is OAuthProviderSlug {
  return value === 'google' || value === 'facebook'
}

/**
 * Handles the `/auth/callback` page after the backend redirects the browser here.
 *
 * Mode login (default):
 *   The backend has already set a new refresh cookie. We call `refreshSession()` to
 *   exchange it for an in-memory access token, then navigate to `returnTo`.
 *
 * Mode link:
 *   The backend forwarded `code` and `state` to this page. Because this arrives after a
 *   full-page redirect, the in-memory access token may be missing: restore the session first.
 *   Then we call `POST /api/v1/auth/oauth2/{provider}/link` with `code`, `state` and an explicit
 *   `Authorization: Bearer <accessToken>` header. On 200, the backend returns the updated
 *   `AuthSession` and rotates the cookie; we store it and navigate to `returnTo`.
 */
export function useOAuthCallback() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const error = searchParams.get('error')
  const mode = searchParams.get('mode')
  const provider = searchParams.get('provider') ?? ''
  const code = searchParams.get('code')
  const state = searchParams.get('state')
  const returnTo = searchParams.get('returnTo')

  const [callbackState, setCallbackState] = useState<CallbackState>(
    error ? { status: 'error', code: error } : { status: 'loading' },
  )

  useEffect(() => {
    if (error) {
      return
    }

    let cancelled = false

    async function run() {
      try {
        if (mode === 'link') {
          if (!isSupportedProvider(provider) || !code || !state) {
            if (!cancelled) setCallbackState({ status: 'error', code: 'OAUTH_STATE_INVALID' })
            return
          }

          setCallbackState({ status: 'linking' })

          // Full-page redirect drops in-memory access token. Restore session first if missing.
          let token = getAccessToken()
          if (!token) {
            const outcome = await refreshSession()
            if (outcome !== 'refreshed') {
              if (!cancelled) setCallbackState({ status: 'error', code: 'UNAUTHORIZED' })
              return
            }
            token = getAccessToken()
          }

          if (!token) {
            if (!cancelled) setCallbackState({ status: 'error', code: 'UNAUTHORIZED' })
            return
          }

          const {
            data,
            response,
            error: linkError,
          } = await apiClient.POST('/api/v1/auth/oauth2/{provider}/link', {
            params: { path: { provider } },
            body: { code, state },
            headers: {
              Authorization: `Bearer ${token}`,
            },
          })

          if (cancelled) return

          if (!response.ok || !data) {
            const errCode =
              linkError &&
              typeof linkError === 'object' &&
              'code' in linkError &&
              typeof linkError.code === 'string'
                ? linkError.code
                : 'unknown'
            setCallbackState({ status: 'error', code: errCode })
            return
          }

          // Link succeeded: store updated session and navigate
          useAuthStore.getState().setSession(data)
          const destination = safeReturnTo(returnTo)
          void navigate(destination, { replace: true })
          return
        }

        // Mode login:
        const outcome = await refreshSession()
        if (cancelled) return

        if (outcome === 'refreshed') {
          const destination = safeReturnTo(returnTo)
          void navigate(destination, { replace: true })
        } else {
          setCallbackState({ status: 'error', code: 'unknown' })
        }
      } catch {
        if (!cancelled) setCallbackState({ status: 'error', code: 'unknown' })
      }
    }

    void run()

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return { callbackState }
}

/** Only allow relative paths that stay within the app. */
function safeReturnTo(returnTo: string | null): string {
  if (
    returnTo &&
    returnTo.startsWith('/') &&
    !returnTo.startsWith('//') &&
    returnTo !== '/auth/callback'
  ) {
    return returnTo
  }
  return '/'
}
