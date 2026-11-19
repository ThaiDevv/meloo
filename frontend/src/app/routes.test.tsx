import { act, cleanup, render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { Providers } from './providers'
import { appRoutes } from './routes'
import { useSessionStore } from '@/shared/session/store'
import { http, HttpResponse } from 'msw'
import { server } from '@/mocks/server'
import users from '../../../contracts/examples/admin/list-admin-users.200.json'
import reports from '../../../contracts/examples/admin/list-admin-reports.200.json'
import audit from '../../../contracts/examples/admin/list-admin-audit-logs.200.json'

function renderAt(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

describe('app routes', () => {
  beforeEach(() => {
    useSessionStore.getState().setRole('ADMIN')
    server.use(
      http.get('*/api/v1/admin/users', () => HttpResponse.json(users)),
      http.get('*/api/v1/admin/reports', () => HttpResponse.json(reports)),
      http.get('*/api/v1/admin/audit-logs', () => HttpResponse.json(audit)),
    )
  })

  it.each([
    ['/admin', 'Hôm nay'],
    ['/admin/catalog', 'Nơi Này Có Anh'],
    ['/admin/playlists', 'Bộ chuẩn V-Pop'],
    ['/admin/users', 'MinhNhac'],
    ['/admin/moderation', 'Ngữ cảnh (2 tin nhắn)'],
    ['/admin/audit', 'USER_LOCK'],
  ])('%s renders the admin page', async (path, content) => {
    renderAt(path)
    expect(await screen.findByText(content)).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Menu quản trị' })).toBeInTheDocument()
    expect(screen.queryByTestId('placeholder')).not.toBeInTheDocument()
  })

  it.each([
    ['/legal/privacy', 'Chính sách quyền riêng tư'],
    ['/legal/terms', 'Điều khoản sử dụng'],
    ['/legal/data-deletion', 'Hướng dẫn xóa dữ liệu'],
  ])('serves %s without a session', async (path, title) => {
    useSessionStore.getState().setRole(null)
    renderAt(path)
    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument()
    expect(screen.queryByTestId('placeholder')).not.toBeInTheDocument()
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

  it('serves the room creation screen at /rooms/new and the lobby entry at /r/:code', async () => {
    renderAt('/rooms/new')
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Tạo phòng')
    cleanup()
    // ABCD is not a room code, so the lobby answers without calling the server.
    renderAt('/r/ABCD')
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      'Mã phòng không hợp lệ',
    )
    expect(screen.queryByTestId('placeholder')).not.toBeInTheDocument()
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

  it('serves the OAuth callback page at /auth/callback', async () => {
    renderAt('/auth/callback')
    expect(
      await screen.findByRole('heading', { name: /đăng nhập không thành công/i }),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('placeholder')).not.toBeInTheDocument()
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
    // Both renders above stay mounted, so the profile page may appear more than once.
    expect(
      await screen.findAllByRole('heading', { level: 2, name: 'Lịch sử trận' }),
    ).not.toHaveLength(0)
    expect(screen.queryByTestId('placeholder')).not.toBeInTheDocument()
    expect(guest.state.location.pathname).toBe('/profile')
  })
})
