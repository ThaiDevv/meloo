import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import fixture from '../../../../../contracts/examples/admin/list-admin-audit-logs.200.json'
import { server } from '@/mocks/server'
import { renderAdmin } from '../shell/testing'
import { AuditPage } from './AuditPage'
import { toUtc } from './dates'

it('filters by actor, time, action and target; preserves filters across pages and resets them', async () => {
  const queries: URLSearchParams[] = []
  server.use(
    http.get('*/api/v1/admin/audit-logs', ({ request }) => {
      const params = new URL(request.url).searchParams
      queries.push(params)
      return HttpResponse.json({ ...fixture, totalPages: 2, page: Number(params.get('page')) })
    }),
  )
  renderAdmin(<AuditPage />)
  await screen.findByText('USER_LOCK')
  await userEvent.type(
    screen.getByLabelText('ID quản trị viên (UUID)'),
    fixture.items[0]?.actorId ?? '',
  )
  await userEvent.type(screen.getByLabelText('Mã thao tác'), 'USER_LOCKED')
  await userEvent.type(screen.getByLabelText('Loại tài nguyên'), 'USER')
  fireEvent.change(screen.getByLabelText('Từ lúc (giờ địa phương)'), {
    target: { value: '2026-10-28T10:00' },
  })
  fireEvent.change(screen.getByLabelText('Đến lúc (giờ địa phương)'), {
    target: { value: '2026-10-30T10:00' },
  })
  await userEvent.click(screen.getByRole('button', { name: 'Lọc' }))
  await waitFor(() => {
    expect(queries.at(-1)?.get('actorId')).toBe(fixture.items[0]?.actorId ?? '')
  })
  expect(queries.at(-1)?.get('from')).toBe(new Date('2026-10-28T10:00').toISOString())
  expect(queries.at(-1)?.get('to')).toBe(new Date('2026-10-30T10:00').toISOString())
  expect(queries.at(-1)?.get('action')).toBe('USER_LOCKED')
  expect(queries.at(-1)?.get('targetType')).toBe('USER')
  await userEvent.click(screen.getByRole('button', { name: 'Trang sau' }))
  await waitFor(() => {
    expect(queries.at(-1)?.get('page')).toBe('1')
  })
  expect(queries.at(-1)?.get('action')).toBe('USER_LOCKED')
  await userEvent.click(screen.getByRole('button', { name: 'Xóa lọc' }))
  await waitFor(() => {
    expect(Object.fromEntries(queries.at(-1) ?? [])).toEqual({ page: '0', size: '20' })
  })
})
it('renders audit metadata, reason and raw detail without mutation controls', async () => {
  server.use(http.get('*/api/v1/admin/audit-logs', () => HttpResponse.json(fixture)))
  renderAdmin(<AuditPage />)
  expect(await screen.findByText('Thực hiện bởi AdminThai')).toBeVisible()
  expect(screen.getByText(/Repeated abusive/)).toBeVisible()
  await userEvent.click(screen.getByText('Chi tiết · #1'))
  expect(screen.getByText(/previousStatus/)).toBeVisible()
  expect(toUtc('')).toBeUndefined()
})
it('shows retry for failure and an empty audit log', async () => {
  server.use(http.get('*/api/v1/admin/audit-logs', () => HttpResponse.json({}, { status: 500 })))
  renderAdmin(<AuditPage />)
  await screen.findByRole('alert')
  server.use(
    http.get('*/api/v1/admin/audit-logs', () => HttpResponse.json({ ...fixture, items: [] })),
  )
  await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('Không có kết quả')).toBeVisible()
})
