import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useAuthStore } from '@/shared/api/authStore'
import { GuestStartPage } from './guest/GuestStartPage'
import { RequireIdentity } from './RequireIdentity'
import { resetSessionRestoreForTests } from './useSessionRestore'

function StartProbe() {
  const location = useLocation()
  return <p>start from {(location.state as { from?: string } | null)?.from}</p>
}

function renderGuard(showGuestPage = false) {
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/profile?tab=stats']}>
          <Routes>
            <Route element={<RequireIdentity />}>
              <Route path="/profile" element={<p>profile</p>} />
            </Route>
            <Route path="/start" element={showGuestPage ? <GuestStartPage /> : <StartProbe />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

const session = {
  accessToken: 'restored-token',
  tokenType: 'Bearer',
  expiresAt: '2026-10-23T13:40:00Z',
  user: {
    id: '7c1d2a64-3b5e-4f08-9a1b-2e6f0c4d8a11',
    kind: 'GUEST',
    role: 'USER',
    nickname: 'Meloman',
    createdAt: '2026-10-23T13:10:00Z',
  },
}

afterEach(() => {
  act(() => {
    useAuthStore.getState().clear()
  })
  resetSessionRestoreForTests()
})

describe('RequireIdentity', () => {
  it('restores the session from the refresh cookie after a reload', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(session)))
    renderGuard()

    expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
    expect(await screen.findByText('profile')).toBeInTheDocument()
    expect(useAuthStore.getState().accessToken).toBe('restored-token')
  })

  it('sends visitors with no session to /start and remembers where they were going', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderGuard()

    expect(await screen.findByText('start from /profile?tab=stats')).toBeInTheDocument()
  })

  it('does not refresh again when the guard redirects a visitor to /start', async () => {
    let refreshCalls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls++
        return HttpResponse.json(
          { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        )
      }),
    )
    renderGuard(true)

    expect(await screen.findByLabelText('Biệt danh')).toBeInTheDocument()
    expect(refreshCalls).toBe(1)
  })

  it('lets a signed-in user straight through without calling refresh', () => {
    let refreshCalls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls++
        return HttpResponse.json(session)
      }),
    )
    useAuthStore.getState().setSession(session as never)
    renderGuard()

    expect(screen.getByText('profile')).toBeInTheDocument()
    expect(refreshCalls).toBe(0)
  })

  it('keeps the player here, with a retry, when the server is unavailable', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Unavailable', status: 503, code: 'UNAVAILABLE' },
          { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderGuard()

    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa khôi phục được phiên')
    expect(screen.queryByText(/^start from/)).not.toBeInTheDocument()

    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(session)))
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('profile')).toBeInTheDocument()
  })

  it('does not treat a repeated REFRESH_RACE as a lost session', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Race', status: 401, code: 'REFRESH_RACE' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderGuard()

    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa khôi phục được phiên')
    expect(screen.queryByText(/^start from/)).not.toBeInTheDocument()
  })
})
