import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useAuthStore } from '@/shared/api/authStore'
import { resetSessionRestoreForTests } from '../useSessionRestore'
import { OAuthCallbackPage } from './OAuthCallbackPage'

const registeredSession = {
  accessToken: 'mock-registered-token',
  tokenType: 'Bearer',
  expiresAt: '2026-10-23T13:25:00Z',
  user: {
    id: 'b41c7d90-2e84-4a3c-9c71-8f21e5b73012',
    kind: 'REGISTERED',
    role: 'USER',
    nickname: 'MinhNhac',
    email: 'minh.nhac@example.com',
    rankPoints: 1450,
    linkedProviders: ['GOOGLE'],
    createdAt: '2026-10-20T09:15:00Z',
  },
}

function renderCallback(search: string) {
  return render(
    <I18nextProvider i18n={i18n}>
      <MemoryRouter initialEntries={[{ pathname: '/auth/callback', search }]}>
        <Routes>
          <Route path="/auth/callback" element={<OAuthCallbackPage />} />
          <Route path="/" element={<p>home</p>} />
          <Route path="/solo" element={<p>solo page</p>} />
        </Routes>
      </MemoryRouter>
    </I18nextProvider>,
  )
}

beforeEach(() => {
  resetSessionRestoreForTests()
})

afterEach(() => {
  act(() => {
    useAuthStore.getState().clear()
  })
  server.events.removeAllListeners()
})

describe('OAuthCallbackPage', () => {
  it('shows loading state initially', () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () => new Promise(() => undefined)), // never resolves
    )
    renderCallback('')
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('navigates to home after successful login', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(registeredSession)))
    renderCallback('')
    await waitFor(() => {
      expect(screen.getByText('home')).toBeInTheDocument()
    })
  })

  it('navigates to returnTo after successful login', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(registeredSession)))
    renderCallback('?returnTo=/solo')
    await waitFor(() => {
      expect(screen.getByText('solo page')).toBeInTheDocument()
    })
  })

  it('shows error state when backend returns ?error=OAUTH_STATE_INVALID', async () => {
    renderCallback('?error=OAUTH_STATE_INVALID')
    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /đăng nhập không thành công/i }),
      ).toBeInTheDocument()
    })
    expect(screen.getByText(/phiên xác thực không hợp lệ/i)).toBeInTheDocument()
  })

  it('shows generic error when refresh fails after login', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json({ code: 'UNAUTHORIZED' }, { status: 401 }),
      ),
    )
    renderCallback('')
    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /đăng nhập không thành công/i }),
      ).toBeInTheDocument()
    })
  })

  it('shows linking state and navigates home after successful link', async () => {
    server.use(
      http.post('*/api/v1/auth/oauth2/google/link', () =>
        HttpResponse.json(registeredSession, { status: 200 }),
      ),
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json(registeredSession)),
    )
    renderCallback('?mode=link&provider=google&code=abc123&state=st_xyz')
    await waitFor(() => {
      expect(screen.getByText('home')).toBeInTheDocument()
    })
  })

  it('error retry button navigates to /start', async () => {
    // Assign location href spy
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

    renderCallback('?error=OAUTH_EMAIL_UNVERIFIED')
    const retryBtn = await screen.findByRole('button', { name: /thử lại/i })
    await userEvent.click(retryBtn)
    expect(assign).toHaveBeenCalledWith('/start')
  })
})
