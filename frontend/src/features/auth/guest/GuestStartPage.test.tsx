import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { resetAuthMock } from '@/features/auth/mocks'
import { server } from '@/mocks/server'
import { useAuthStore } from '@/shared/api/authStore'
import { GuestStartPage } from './GuestStartPage'
import { useAvatarStore } from './store'

function renderPage(initialEntry: string | { pathname: string; state: unknown } = '/start') {
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}
      >
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/start" element={<GuestStartPage />} />
            <Route path="/" element={<p>home</p>} />
            <Route path="/solo" element={<p>solo</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

/** Records every POST /auth/guest body; answers with the mock server unless overridden. */
function spyOnGuestRequests() {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === 'POST' && request.url.endsWith('/api/v1/auth/guest')) {
      bodies.push(await request.clone().json())
    }
  })
  return bodies
}

beforeEach(() => {
  resetAuthMock()
})

afterEach(() => {
  server.events.removeAllListeners()
  useAuthStore.getState().clear()
  useAvatarStore.getState().setAvatarId(null)
})

describe('GuestStartPage', () => {
  it('has one h1 and a labelled nickname field with its limit next to it', () => {
    renderPage()

    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
    const field = screen.getByLabelText('Biệt danh')
    expect(field).toHaveAccessibleDescription('Từ 3 đến 20 ký tự.')
  })

  it.each([
    ['', 'Hãy nhập biệt danh.'],
    ['   ', 'Hãy nhập biệt danh.'],
    ['ab', 'Biệt danh cần ít nhất 3 ký tự.'],
    ['a'.repeat(21), 'Biệt danh tối đa 20 ký tự.'],
  ])('rejects %j before sending anything (BR-22)', async (nickname, message) => {
    const bodies = spyOnGuestRequests()
    const user = userEvent.setup()
    renderPage()

    if (nickname) await user.type(screen.getByLabelText('Biệt danh'), nickname)
    await user.click(screen.getByRole('button', { name: 'Vào chơi' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(screen.getByLabelText('Biệt danh')).toBeInvalid()
    expect(bodies).toEqual([])
  })

  it('trims the nickname, sends it and lets the guest in with the chosen avatar', async () => {
    const bodies = spyOnGuestRequests()
    const user = userEvent.setup()
    renderPage({ pathname: '/start', state: { from: '/solo' } })

    await user.type(screen.getByLabelText('Biệt danh'), '  Meloman  ')
    await user.click(screen.getByRole('radio', { name: 'Piano' }))
    await user.click(screen.getByRole('button', { name: 'Vào chơi' }))

    expect(await screen.findByText('solo')).toBeInTheDocument()
    expect(bodies).toEqual([{ nickname: 'Meloman' }])
    expect(useAuthStore.getState().user?.nickname).toBe('Meloman')
    expect(useAvatarStore.getState().avatarId).toBe('piano')
  })

  it('goes home when no page asked for the sign-in, and ignores a `from` that leaves the site', async () => {
    const user = userEvent.setup()
    renderPage({ pathname: '/start', state: { from: '//evil.example' } })

    await user.type(screen.getByLabelText('Biệt danh'), 'Meloman')
    await user.click(screen.getByRole('button', { name: 'Vào chơi' }))

    expect(await screen.findByText('home')).toBeInTheDocument()
  })

  it('offers all avatars as one radio group with the first picked, and moves with the arrow keys', async () => {
    const user = userEvent.setup()
    renderPage()

    const group = screen.getByRole('group', { name: 'Avatar' })
    const radios = screen.getAllByRole('radio')
    expect(group).toBeInTheDocument()
    expect(radios).toHaveLength(8)
    expect(radios[0]).toBeChecked()

    radios[0]?.focus()
    await user.keyboard('{ArrowRight}')
    expect(radios[1]).toBeChecked()
  })

  it('shows the field error when the server rejects the nickname (banned word)', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Biệt danh'), 'xxbadwordxx')
    await user.click(screen.getByRole('button', { name: 'Vào chơi' }))

    expect(await screen.findByText(/không dùng được/)).toBeInTheDocument()
    expect(screen.getByLabelText('Biệt danh')).toBeInvalid()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('tells the player to wait on 429 and to check the connection on other failures', async () => {
    const user = userEvent.setup()
    renderPage()
    server.use(
      http.post('*/api/v1/auth/guest', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Too many', status: 429, code: 'RATE_LIMITED' },
          { status: 429, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    await user.type(screen.getByLabelText('Biệt danh'), 'Meloman')
    await user.click(screen.getByRole('button', { name: 'Vào chơi' }))
    expect(await screen.findByText(/thử quá nhanh/)).toBeInTheDocument()

    server.use(http.post('*/api/v1/auth/guest', () => HttpResponse.error()))
    await user.click(screen.getByRole('button', { name: 'Vào chơi' }))
    await waitFor(() => {
      expect(screen.getByText(/Chưa vào được/)).toBeInTheDocument()
    })
  })

  it('skips the form for someone who is already in', () => {
    useAuthStore.getState().setSession({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresAt: '2026-10-23T13:25:00Z',
      user: {
        id: '7c1d2a64-3b5e-4f08-9a1b-2e6f0c4d8a11',
        kind: 'GUEST',
        role: 'USER',
        nickname: 'Meloman',
        createdAt: '2026-10-23T13:10:00Z',
      },
    })
    renderPage()

    expect(screen.getByText('home')).toBeInTheDocument()
  })

  it('renders in English too', async () => {
    await i18n.changeLanguage('en')
    renderPage()

    expect(screen.getByLabelText('Nickname')).toHaveAccessibleDescription('3 to 20 characters.')
    expect(screen.getByRole('button', { name: 'Start playing' })).toBeInTheDocument()
  })
})
