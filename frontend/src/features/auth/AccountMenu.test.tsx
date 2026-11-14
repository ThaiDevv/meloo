import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useAuthStore, type AuthSession } from '@/shared/api/authStore'
import { useSessionStore } from '@/shared/session/store'
import { useToastStore } from '@/shared/ui/toast-store'
import { resetSessionRestoreForTests } from './useSessionRestore'
import { AccountMenu } from './AccountMenu'

const registeredSession: AuthSession = {
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

const adminSession: AuthSession = {
  accessToken: 'mock-admin-token',
  tokenType: 'Bearer',
  expiresAt: '2026-10-23T13:25:00Z',
  user: {
    id: 'c41c7d90-2e84-4a3c-9c71-8f21e5b73099',
    kind: 'REGISTERED',
    role: 'ADMIN',
    nickname: 'AdminMeloo',
    email: 'admin@example.com',
    rankPoints: 2000,
    linkedProviders: ['GOOGLE'],
    createdAt: '2026-10-20T09:15:00Z',
  },
}

const guestSession: AuthSession = {
  accessToken: 'mock-guest-token',
  tokenType: 'Bearer',
  expiresAt: '2026-10-23T13:25:00Z',
  user: {
    id: 'a41c7d90-2e84-4a3c-9c71-8f21e5b73001',
    kind: 'GUEST',
    role: 'USER',
    nickname: 'Khach123',
    rankPoints: 1000,
    linkedProviders: [],
    createdAt: '2026-10-20T09:15:00Z',
  },
}

function renderMenu(initialPath = '/') {
  return render(
    <I18nextProvider i18n={i18n}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/" element={<AccountMenu />} />
          <Route path="/profile" element={<p>profile page</p>} />
        </Routes>
      </MemoryRouter>
    </I18nextProvider>,
  )
}

beforeEach(() => {
  useToastStore.setState({ toasts: [] })
  resetSessionRestoreForTests()
})

afterEach(() => {
  cleanup()
  act(() => {
    useAuthStore.getState().clear()
    useSessionStore.getState().setRole(null)
  })
  resetSessionRestoreForTests()
})

describe('AccountMenu', () => {
  it('shows skeleton while checking and then "Sign in" button when unauthenticated', async () => {
    renderMenu()
    expect(screen.getByTestId('account-menu-skeleton')).toBeInTheDocument()
    expect(await screen.findByTestId('account-menu-login-btn')).toBeInTheDocument()
    expect(screen.queryByTestId('account-menu-skeleton')).not.toBeInTheDocument()
  })

  it('shows "Sign in" button immediately for guests', () => {
    act(() => {
      useAuthStore.getState().setSession(guestSession)
    })
    renderMenu()
    expect(screen.getByTestId('account-menu-login-btn')).toBeInTheDocument()
    expect(screen.queryByTestId('account-menu-skeleton')).not.toBeInTheDocument()
  })

  it('expands to show Google login button when login button is clicked, and closes on Escape', async () => {
    act(() => {
      useAuthStore.getState().setSession(guestSession)
    })
    renderMenu()
    const loginBtn = screen.getByTestId('account-menu-login-btn')
    await userEvent.click(loginBtn)
    expect(screen.getByTestId('btn-login-google')).toBeInTheDocument()

    // Pressing Escape closes the panel and focuses the trigger button
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByTestId('btn-login-google')).not.toBeInTheDocument()
    expect(loginBtn).toHaveFocus()
  })

  it('closes login panel when clicking outside', async () => {
    act(() => {
      useAuthStore.getState().setSession(guestSession)
    })
    renderMenu()
    const loginBtn = screen.getByTestId('account-menu-login-btn')
    await userEvent.click(loginBtn)
    expect(screen.getByTestId('btn-login-google')).toBeInTheDocument()

    // Clicking outside closes the panel
    await userEvent.click(document.body)
    expect(screen.queryByTestId('btn-login-google')).not.toBeInTheDocument()
  })

  it('shows account menu trigger for registered users', () => {
    act(() => {
      useAuthStore.getState().setSession(registeredSession)
    })
    renderMenu()
    expect(screen.getByTestId('account-menu-trigger')).toBeInTheDocument()
  })

  it('shows the saved avatar in the account menu', () => {
    act(() => {
      useAuthStore
        .getState()
        .setSession({
          ...registeredSession,
          user: { ...registeredSession.user, avatarId: 'piano' },
        })
    })
    renderMenu()
    expect(
      screen.getByTestId('account-menu-trigger').querySelector('[data-avatar-id="piano"]'),
    ).toBeInTheDocument()
  })

  it('shows account menu trigger for admin users', () => {
    act(() => {
      useAuthStore.getState().setSession(adminSession)
    })
    renderMenu()
    expect(screen.getByTestId('account-menu-trigger')).toBeInTheDocument()
  })

  it('opens dropdown menu when trigger is clicked and closes on Escape', async () => {
    act(() => {
      useAuthStore.getState().setSession(registeredSession)
    })
    renderMenu()
    const trigger = screen.getByTestId('account-menu-trigger')
    await userEvent.click(trigger)
    expect(screen.getByTestId('account-menu-profile')).toBeInTheDocument()
    expect(screen.getByTestId('account-menu-logout')).toBeInTheDocument()

    // Press Escape to close menu
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByTestId('account-menu-profile')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('closes dropdown menu when clicking outside', async () => {
    act(() => {
      useAuthStore.getState().setSession(registeredSession)
    })
    renderMenu()
    const trigger = screen.getByTestId('account-menu-trigger')
    await userEvent.click(trigger)
    expect(screen.getByTestId('account-menu-profile')).toBeInTheDocument()

    // Clicking outside closes the menu
    await userEvent.click(document.body)
    expect(screen.queryByTestId('account-menu-profile')).not.toBeInTheDocument()
  })

  it('signs out and clears session when logout succeeds (HTTP 204)', async () => {
    act(() => {
      useAuthStore.getState().setSession(registeredSession)
    })
    server.use(http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })))
    renderMenu()
    await userEvent.click(screen.getByTestId('account-menu-trigger'))
    await userEvent.click(screen.getByTestId('account-menu-logout'))
    // After logout, role becomes null so the sign-in button appears
    expect(await screen.findByTestId('account-menu-login-btn')).toBeInTheDocument()
    expect(useSessionStore.getState().role).toBeNull()
  })

  it('shows toast error and preserves session when logout fails with HTTP 500 [M3]', async () => {
    act(() => {
      useAuthStore.getState().setSession(registeredSession)
    })
    server.use(
      http.post('*/api/v1/auth/logout', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
    )
    renderMenu()
    await userEvent.click(screen.getByTestId('account-menu-trigger'))
    await userEvent.click(screen.getByTestId('account-menu-logout'))

    // Session is NOT cleared
    expect(useSessionStore.getState().role).toBe('USER')
    expect(useAuthStore.getState().accessToken).toBe('mock-registered-token')

    // Toast error was dispatched
    const toasts = useToastStore.getState().toasts
    expect(toasts.length).toBeGreaterThan(0)
    expect(toasts.at(-1)?.tone).toBe('negative')
    expect(toasts.at(-1)?.title).toBe('Chưa đăng xuất được. Thử lại sau.')
  })

  it('cold-load restores registered session on home page [M4]', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(registeredSession)))

    // Cold-load: store has null role and null accessToken
    expect(useSessionStore.getState().role).toBeNull()
    expect(useAuthStore.getState().accessToken).toBeNull()

    renderMenu('/')

    // Initially shows skeleton
    expect(screen.getByTestId('account-menu-skeleton')).toBeInTheDocument()

    // Restores session automatically and renders the registered account menu trigger
    expect(await screen.findByTestId('account-menu-trigger')).toBeInTheDocument()
    expect(screen.queryByTestId('account-menu-skeleton')).not.toBeInTheDocument()
    expect(useSessionStore.getState().role).toBe('USER')
  })
})
