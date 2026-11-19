import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
import { beforeEach, expect, it } from 'vitest'
import userFixture from '../../../../../contracts/examples/admin/get-admin-user.200.json'
import usersFixture from '../../../../../contracts/examples/admin/list-admin-users.200.json'
import { server } from '@/mocks/server'
import { renderAdmin } from '../shell/testing'
import { UsersPage } from './UsersPage'

beforeEach(() => {
  server.use(
    http.get('*/api/v1/admin/users', () => HttpResponse.json(usersFixture)),
    http.get('*/api/v1/admin/users/:id', () => HttpResponse.json(userFixture)),
  )
})
it('searches accounts and paginates then resets page on a new search', async () => {
  const queries: URLSearchParams[] = []
  server.use(
    http.get('*/api/v1/admin/users', ({ request }) => {
      const params = new URL(request.url).searchParams
      queries.push(params)
      return HttpResponse.json({ ...usersFixture, page: Number(params.get('page')), totalPages: 2 })
    }),
  )
  renderAdmin(<UsersPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Trang sau' }))
  await waitFor(() => {
    expect(queries.at(-1)?.get('page')).toBe('1')
  })
  await userEvent.type(screen.getByLabelText('Biệt danh hoặc email'), 'Minh')
  await userEvent.click(screen.getByRole('button', { name: 'Lọc' }))
  await waitFor(() => {
    expect(Object.fromEntries(queries.at(-1) ?? [])).toEqual({
      q: 'Minh',
      page: '0',
      size: '20',
    })
  })
})
it('loads account detail, cancels without writing and confirms lock/unlock/role with exact bodies', async () => {
  let current = { ...userFixture }
  const bodies: unknown[] = []
  server.use(
    http.get('*/api/v1/admin/users/:id', () => HttpResponse.json(current)),
    http.put('*/api/v1/admin/users/:id/lock', async ({ request }) => {
      const body = await request.json()
      bodies.push(body)
      if (typeof body === 'object' && body && 'locked' in body)
        current = { ...current, status: body.locked ? 'LOCKED' : 'ACTIVE' }
      return HttpResponse.json(current)
    }),
    http.put('*/api/v1/admin/users/:id/role', async ({ request }) => {
      bodies.push(await request.json())
      current = { ...current, role: 'ADMIN' }
      return HttpResponse.json(current)
    }),
  )
  renderAdmin(<UsersPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem MinhNhac' }))
  expect(await screen.findByText(userFixture.id)).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Khóa tài khoản' }))
  let dialog = screen.getByRole('dialog')
  expect(within(dialog).getByRole('button', { name: 'Xác nhận' })).toBeDisabled()
  await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy' }))
  expect(bodies).toEqual([])
  await userEvent.click(screen.getByRole('button', { name: 'Khóa tài khoản' }))
  dialog = screen.getByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText('Lý do khóa'), '  Spam  ')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }))
  await screen.findByText('Đã lưu thay đổi.')
  expect(bodies[0]).toEqual({ locked: true, reason: 'Spam' })
  await userEvent.click(screen.getByRole('button', { name: 'Mở khóa tài khoản' }))
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
  await screen.findByRole('button', { name: 'Khóa tài khoản' })
  expect(bodies[1]).toEqual({ locked: false, reason: null })
  await userEvent.click(screen.getByRole('button', { name: 'Đổi vai trò' }))
  expect(within(screen.getByRole('dialog')).getByText(/Quản trị viên/)).toBeVisible()
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
  await waitFor(() => {
    expect(bodies[2]).toEqual({ role: 'ADMIN' })
  })
})
it('retains lock form and reason on forbidden self action', async () => {
  server.use(
    http.put('*/api/v1/admin/users/:id/lock', () =>
      HttpResponse.json({ code: 'ADMIN_SELF_ACTION_FORBIDDEN' }, { status: 409 }),
    ),
  )
  renderAdmin(<UsersPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem MinhNhac' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Khóa tài khoản' }))
  await userEvent.type(screen.getByLabelText('Lý do khóa'), 'Spam')
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Bạn không thể tự khóa')
  expect(screen.getByLabelText('Lý do khóa')).toHaveValue('Spam')
})
it('does not offer role changes for guest accounts', async () => {
  server.use(
    http.get('*/api/v1/admin/users/:id', () =>
      HttpResponse.json({ ...userFixture, kind: 'GUEST' }),
    ),
  )
  renderAdmin(<UsersPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem MinhNhac' }))
  await screen.findByText(userFixture.id)
  expect(screen.queryByRole('button', { name: 'Đổi vai trò' })).not.toBeInTheDocument()
})
it('shows loading, server errors, retry and empty states', async () => {
  server.use(
    http.get('*/api/v1/admin/users', async () => {
      await delay(30)
      return HttpResponse.json({}, { status: 403 })
    }),
  )
  renderAdmin(<UsersPage />)
  expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
  await screen.findByRole('alert')
  server.use(
    http.get('*/api/v1/admin/users', () =>
      HttpResponse.json({ ...usersFixture, items: [], totalPages: 0 }),
    ),
  )
  await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('Không có kết quả')).toBeVisible()
})

it('holds the confirmation open and disables repeat submissions while a write is pending', async () => {
  let release: (() => void) | undefined
  let calls = 0
  server.use(
    http.put('*/api/v1/admin/users/:id/lock', async () => {
      calls++
      await new Promise<void>((resolve) => {
        release = resolve
      })
      return HttpResponse.json({ ...userFixture, status: 'LOCKED' })
    }),
  )
  renderAdmin(<UsersPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem MinhNhac' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Khóa tài khoản' }))
  await userEvent.type(screen.getByLabelText('Lý do khóa'), 'Spam')
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(await screen.findByRole('button', { name: 'Đang lưu…' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Hủy' })).toBeDisabled()
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Đóng' }))
  expect(screen.getByRole('dialog')).toBeVisible()
  expect(calls).toBe(1)
  release?.()
  await screen.findByText('Đã lưu thay đổi.')
})
