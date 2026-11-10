import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { act } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { useSessionStore } from '@/shared/session/store'
import { buildOAuthUrl, startOAuth } from './oauthUrl'
import { SocialLoginButtons } from './SocialLoginButtons'

function renderButtons(returnTo?: string) {
  return render(
    <I18nextProvider i18n={i18n}>
      <MemoryRouter>
        <SocialLoginButtons returnTo={returnTo} />
      </MemoryRouter>
    </I18nextProvider>,
  )
}

afterEach(() => {
  act(() => {
    useSessionStore.getState().setRole(null)
  })
  // Reset env variable mock
  import.meta.env.VITE_FEATURE_FACEBOOK = undefined
})

describe('SocialLoginButtons', () => {
  it('renders Google login button', () => {
    renderButtons()
    expect(screen.getByTestId('btn-login-google')).toBeInTheDocument()
  })

  it('does NOT render Facebook button when VITE_FEATURE_FACEBOOK is not set', () => {
    import.meta.env.VITE_FEATURE_FACEBOOK = undefined
    renderButtons()
    expect(screen.queryByTestId('btn-login-facebook')).not.toBeInTheDocument()
  })

  it('renders Facebook button when VITE_FEATURE_FACEBOOK is "true"', () => {
    import.meta.env.VITE_FEATURE_FACEBOOK = 'true'
    renderButtons()
    expect(screen.getByTestId('btn-login-facebook')).toBeInTheDocument()
  })

  it('buildOAuthUrl builds absolute URL with backend origin and query params', () => {
    const url = buildOAuthUrl('google', { mode: 'login', returnTo: '/solo' })
    const parsed = new URL(url, 'http://localhost')
    expect(parsed.pathname).toBe('/api/v1/auth/oauth2/google')
    expect(parsed.searchParams.get('mode')).toBe('login')
    expect(parsed.searchParams.get('returnTo')).toBe('/solo')
  })

  it('buildOAuthUrl supports link mode without returnTo', () => {
    const url = buildOAuthUrl('facebook', { mode: 'link' })
    const parsed = new URL(url, 'http://localhost')
    expect(parsed.pathname).toBe('/api/v1/auth/oauth2/facebook')
    expect(parsed.searchParams.get('mode')).toBe('link')
    expect(parsed.searchParams.get('returnTo')).toBeNull()
  })

  it('navigates to OAuth start URL when Google login button is clicked', async () => {
    const assign = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { href: '', origin: 'http://localhost' },
      writable: true,
    })
    Object.defineProperty(window.location, 'href', {
      set: assign,
      get: () => '',
      configurable: true,
    })

    renderButtons('/solo')
    await userEvent.click(screen.getByTestId('btn-login-google'))
    expect(assign).toHaveBeenCalled()
    const targetUrl = String(assign.mock.calls[0]?.[0] ?? '')
    expect(targetUrl).toContain('/api/v1/auth/oauth2/google')
    expect(targetUrl).toContain('mode=login')
    expect(targetUrl).toContain('returnTo=%2Fsolo')
  })

  it('startOAuth navigates to built URL', () => {
    const assign = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { href: '', origin: 'http://localhost' },
      writable: true,
    })
    Object.defineProperty(window.location, 'href', {
      set: assign,
      get: () => '',
      configurable: true,
    })

    startOAuth('google', { mode: 'link', returnTo: '/profile' })
    expect(assign).toHaveBeenCalled()
    const targetUrl = String(assign.mock.calls[0]?.[0] ?? '')
    expect(targetUrl).toContain('/api/v1/auth/oauth2/google')
    expect(targetUrl).toContain('mode=link')
  })
})
