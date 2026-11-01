import { act, cleanup, render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { Providers } from './providers'
import { appRoutes } from './routes'
import { useSessionStore } from '@/shared/session/store'

function renderAt(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// Every route in .claude/skills/meloo-frontend/references/routes.md → ticket that replaces it.
const routes: [path: string, ticket: string][] = [
  ['/auth/callback', 'FE-AUTH-02'],
  ['/daily', 'FE-DAILY-01'],
  ['/rooms/new', 'FE-ROOM-01'],
  ['/r/ABCD', 'FE-ROOM-01'],
  ['/r/ABCD/play', 'FE-ROOM-02'],
  ['/rooms', 'FE-ROOM-03'],
  ['/quick-match', 'FE-ROOM-03'],
  ['/profile', 'FE-PROF-01'],
  ['/profile/matches/m1', 'FE-PROF-01'],
  ['/leaderboard', 'FE-LB-01'],
  ['/admin', 'FE-ADM-01'],
  ['/admin/catalog', 'FE-ADM-01'],
  ['/admin/playlists', 'FE-ADM-01'],
  ['/admin/users', 'FE-ADM-02'],
  ['/admin/moderation', 'FE-ADM-02'],
  ['/admin/audit', 'FE-ADM-02'],
  ['/legal/privacy', 'LEG-01'],
  ['/legal/terms', 'LEG-01'],
  ['/legal/data-deletion', 'LEG-01'],
]

describe('app routes', () => {
  beforeEach(() => {
    useSessionStore.getState().setRole('ADMIN')
  })

  it.each(routes)('%s renders the placeholder of %s', async (path, ticket) => {
    renderAt(path)
    const placeholder = await screen.findByTestId('placeholder')
    expect(placeholder).toHaveTextContent(ticket)
  })

  it('serves the home page at / and the solo setup at /solo', async () => {
    renderAt('/')
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      'Nghe ít. Đoán nhanh.',
    )
    cleanup()
    renderAt('/solo')
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Chơi đơn')
  })

  it('serves the single-play screen at /solo/:matchId', async () => {
    renderAt('/solo/abc')
    expect(await screen.findByText('Không tìm thấy trận này')).toBeInTheDocument()
    expect(screen.queryByTestId('placeholder')).not.toBeInTheDocument()
  })

  it('serves the component gallery at /_ui', async () => {
    renderAt('/_ui')
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Thư viện giao diện' }),
    ).toBeInTheDocument()
  })

  it('shows not-found for unknown paths', async () => {
    renderAt('/nope')
    expect(await screen.findByText('Không tìm thấy trang')).toBeInTheDocument()
    expect(screen.queryByTestId('placeholder')).not.toBeInTheDocument()
  })

  it('redirects /admin to home unless the role is ADMIN', async () => {
    useSessionStore.getState().setRole('USER')
    const router = renderAt('/admin/users')
    await screen.findByRole('heading', { level: 1, name: 'Nghe ít. Đoán nhanh.' })
    expect(router.state.location.pathname).toBe('/')
  })

  it('redirects /profile to /start for anonymous visitors, lets guests in', async () => {
    useSessionStore.getState().setRole(null)
    const router = renderAt('/profile')
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      'Đặt biệt danh là vào chơi được',
    )
    expect(router.state.location.pathname).toBe('/start')

    act(() => {
      useSessionStore.getState().setRole('GUEST')
    })
    const guest = renderAt('/profile')
    await screen.findAllByTestId('placeholder')
    expect(guest.state.location.pathname).toBe('/profile')
  })
})
