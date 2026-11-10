import { DEFAULT_BASE_URL } from '@/shared/api/baseUrl'

export interface BuildOAuthUrlOptions {
  mode?: 'login' | 'link'
  returnTo?: string
}

/** Dựng URL OAuth endpoint từ backend base URL chung, giữ query params mode và returnTo */
export function buildOAuthUrl(
  provider: 'google' | 'facebook',
  options: BuildOAuthUrlOptions = {},
): string {
  const mode = options.mode ?? 'login'
  const params = new URLSearchParams({ mode })
  if (options.returnTo) params.set('returnTo', options.returnTo)

  const path = `/api/v1/auth/oauth2/${provider}?${params.toString()}`
  if (!DEFAULT_BASE_URL) return path

  return new URL(path, DEFAULT_BASE_URL).toString()
}

/** Initiates an OAuth2 login flow by navigating to the backend start URL. */
export function startOAuth(provider: 'google' | 'facebook', options: BuildOAuthUrlOptions = {}) {
  window.location.href = buildOAuthUrl(provider, options)
}
