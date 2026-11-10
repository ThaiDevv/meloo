import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
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
 *   The backend forwarded `code` and `state` to this page. We call
 *   `POST /api/v1/auth/oauth2/{provider}/link` with these params (the HttpOnly
 *   state/PKCE cookie rides along automatically), then refresh to get the updated user.
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

          const { response } = await apiClient.POST('/api/v1/auth/oauth2/{provider}/link', {
            params: { path: { provider } },
            body: { code, state },
          })

          if (!cancelled && !response.ok) {
            setCallbackState({ status: 'error', code: 'unknown' })
            return
          }
        }

        // For both login and link: refresh to get the new/updated session into memory.
        const outcome = await refreshSession()
        if (cancelled) return

        if (outcome === 'refreshed') {
          const destination = safeReturnTo(returnTo)
          void navigate(destination, { replace: true })
        } else {
          // Session expired or unavailable right after the callback: treat as error.
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
