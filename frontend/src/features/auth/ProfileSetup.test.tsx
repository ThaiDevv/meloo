import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { useAuthStore, type AuthSession } from '@/shared/api/authStore'
import { ProfileSetup } from './ProfileSetup'

const session: AuthSession = {
  accessToken: 'token',
  tokenType: 'Bearer',
  expiresAt: '2028-01-01T00:00:00Z',
  user: {
    id: '123',
    kind: 'REGISTERED',
    role: 'USER',
    nickname: 'Player',
    profileCompleted: false,
    createdAt: '2028-01-01T00:00:00Z',
  },
}
function renderSetup() {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <ProfileSetup />
    </QueryClientProvider>,
  )
}
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  useAuthStore.getState().setSession(session)
})
afterEach(() => {
  act(() => {
    useAuthStore.getState().clear()
  })
  vi.restoreAllMocks()
})
describe('initial profile setup', () => {
  it('validates, previews and persists the chosen avatar before completing the session', async () => {
    let body: unknown
    server.use(
      http.patch('*/api/v1/me/profile', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          ...session.user,
          nickname: 'Meloman',
          avatarId: 'piano',
          profileCompleted: true,
        })
      }),
    )
    const user = userEvent.setup()
    renderSetup()
    await user.click(screen.getByRole('button', { name: 'Lưu và tiếp tục' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Biệt danh'), 'Meloman')
    await user.click(screen.getByRole('radio', { name: 'Piano' }))
    expect(document.querySelector('[data-avatar-id="piano"]')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Lưu và tiếp tục' }))
    await waitFor(() => {
      expect(useAuthStore.getState().user?.profileCompleted).toBe(true)
    })
    expect(body).toEqual({ nickname: 'Meloman', avatarId: 'piano' })
    expect(useAuthStore.getState().user?.avatarId).toBe('piano')
  })
  it('disables repeated submission while saving', async () => {
    let attempts = 0
    let release: (() => void) | undefined
    server.use(
      http.patch('*/api/v1/me/profile', async () => {
        attempts++
        await new Promise<void>((resolve) => {
          release = resolve
        })
        return HttpResponse.json({
          ...session.user,
          nickname: 'Meloman',
          avatarId: 'guitar',
          profileCompleted: true,
        })
      }),
    )
    const user = userEvent.setup()
    renderSetup()
    await user.type(screen.getByLabelText('Biệt danh'), 'Meloman')
    await user.click(screen.getByRole('button', { name: 'Lưu và tiếp tục' }))
    const button = await screen.findByRole('button', { name: 'Đang lưu…' })
    expect(button).toBeDisabled()
    await user.click(button)
    expect(attempts).toBe(1)
    release?.()
    await waitFor(() => {
      expect(useAuthStore.getState().user?.profileCompleted).toBe(true)
    })
  })

  it('keeps the dialog open after an error and permits retry', async () => {
    let attempts = 0
    server.use(
      http.patch('*/api/v1/me/profile', () => {
        attempts++
        return attempts === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json({
              ...session.user,
              nickname: 'Meloman',
              avatarId: 'guitar',
              profileCompleted: true,
            })
      }),
    )
    const user = userEvent.setup()
    renderSetup()
    await user.type(screen.getByLabelText('Biệt danh'), 'Meloman')
    await user.click(screen.getByRole('button', { name: 'Lưu và tiếp tục' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(useAuthStore.getState().user?.profileCompleted).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Lưu và tiếp tục' }))
    await waitFor(() => {
      expect(useAuthStore.getState().user?.profileCompleted).toBe(true)
    })
    expect(attempts).toBe(2)
  })
})
