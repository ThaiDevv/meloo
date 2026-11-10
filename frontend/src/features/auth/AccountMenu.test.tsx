import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useAuthStore } from '@/shared/api/authStore'
import { useSessionStore } from '@/shared/session/store'
import { resetSessionRestoreForTests } from './useSessionRestore'
import { AccountMenu } from './AccountMenu'

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

function setRole(role: 'GUEST' | 'USER' | 'ADMIN' | null) {
  act(() => {
    useSessionStore.getState().setRole(role)
  })
}

beforeEach(() => {
  resetSessionRestoreForTests()
})

afterEach(() => {
  act(() => {
    useAuthStore.getState().clear()
    useSessionStore.getState().setRole(null)
  })
})

describe('AccountMenu', () => {
  it('shows "Sign in" button when not signed in (role === null)', () => {
    setRole(null)
    renderMenu()
    expect(screen.getByTestId('account-menu-login-btn')).toBeInTheDocument()
  })

  it('shows "Sign in" button for guests', () => {
    setRole('GUEST')
    renderMenu()
    expect(screen.getByTestId('account-menu-login-btn')).toBeInTheDocument()
  })

  it('expands to show Google login button when login button is clicked', async () => {
    setRole('GUEST')
    renderMenu()
    await userEvent.click(screen.getByTestId('account-menu-login-btn'))
    expect(screen.getByTestId('btn-login-google')).toBeInTheDocument()
  })

  it('shows account menu trigger for registered users', () => {
    setRole('USER')
    renderMenu()
    expect(screen.getByTestId('account-menu-trigger')).toBeInTheDocument()
  })

  it('shows account menu trigger for admin users', () => {
    setRole('ADMIN')
    renderMenu()
    expect(screen.getByTestId('account-menu-trigger')).toBeInTheDocument()
  })

  it('opens dropdown menu when trigger is clicked', async () => {
    setRole('USER')
    renderMenu()
    await userEvent.click(screen.getByTestId('account-menu-trigger'))
    expect(screen.getByTestId('account-menu-profile')).toBeInTheDocument()
    expect(screen.getByTestId('account-menu-logout')).toBeInTheDocument()
  })

  it('signs out and clears session when logout is clicked', async () => {
    setRole('USER')
    server.use(http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })))
    renderMenu()
    await userEvent.click(screen.getByTestId('account-menu-trigger'))
    await userEvent.click(screen.getByTestId('account-menu-logout'))
    // After logout, role becomes null so the sign-in button appears
    expect(await screen.findByTestId('account-menu-login-btn')).toBeInTheDocument()
  })
})
