import { fireEvent, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, it } from 'vitest'
import reports from '../../../../../contracts/examples/admin/list-admin-reports.200.json'
import words from '../../../../../contracts/examples/admin/list-admin-banned-words.200.json'
import schedule from '../../../../../contracts/examples/admin/list-admin-daily-schedule.200.json'
import tracks from '../../../../../contracts/examples/admin/list-admin-tracks.200.json'
import { server } from '@/mocks/server'
import { renderAdmin } from '../shell/testing'
import { ModerationPage } from './ModerationPage'
import { vietnamToday } from './dates'

beforeEach(() => {
  server.use(
    http.get('*/api/v1/admin/reports', () => HttpResponse.json(reports)),
    http.get('*/api/v1/admin/banned-words', () => HttpResponse.json(words)),
    http.get('*/api/v1/admin/daily-schedule', () => HttpResponse.json(schedule)),
    http.get('*/api/v1/admin/tracks', () => HttpResponse.json(tracks)),
  )
})
it('shows report context, locks with required reason and sends the decision only after confirmation', async () => {
  const bodies: unknown[] = []
  server.use(
    http.post('*/api/v1/admin/reports/:id/resolve', async ({ request }) => {
      bodies.push(await request.json())
      return HttpResponse.json(reports.items[0])
    }),
  )
  renderAdmin(<ModerationPage />)
  await screen.findByText('Ngữ cảnh (2 tin nhắn)')
  await userEvent.click(screen.getByText('Ngữ cảnh (2 tin nhắn)'))
  expect(screen.getByText('Chào mọi người')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Xử lý báo cáo' }))
  expect(bodies).toEqual([])
  const dialog = screen.getByRole('dialog')
  await userEvent.click(within(dialog).getByRole('checkbox'))
  expect(within(dialog).getByRole('button', { name: 'Xác nhận' })).toBeDisabled()
  await userEvent.type(within(dialog).getByLabelText('Lý do khóa'), 'Spam')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }))
  await screen.findByText('Đã lưu thay đổi.')
  expect(bodies).toEqual([{ status: 'ACTIONED', lockSender: true, lockReason: 'Spam' }])
})
it('filters reports, cancels dismissal without writes then confirms it without locking', async () => {
  const bodies: unknown[] = [],
    queries: string[] = []
  server.use(
    http.get('*/api/v1/admin/reports', ({ request }) => {
      queries.push(new URL(request.url).searchParams.get('status') ?? '')
      return HttpResponse.json(reports)
    }),
    http.post('*/api/v1/admin/reports/:id/resolve', async ({ request }) => {
      bodies.push(await request.json())
      return HttpResponse.json(reports.items[0])
    }),
  )
  renderAdmin(<ModerationPage />)
  await userEvent.selectOptions(screen.getByLabelText('Trạng thái báo cáo'), 'DISMISSED')
  await waitFor(() => {
    expect(queries.at(-1)).toBe('DISMISSED')
  })
  await userEvent.click(await screen.findByRole('button', { name: 'Bỏ qua báo cáo' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Hủy' }))
  expect(bodies).toEqual([])
  await userEvent.click(screen.getByRole('button', { name: 'Bỏ qua báo cáo' }))
  expect(within(screen.getByRole('dialog')).queryByRole('checkbox')).toBeNull()
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
  await waitFor(() => {
    expect(bodies).toEqual([{ status: 'DISMISSED', lockSender: false, lockReason: null }])
  })
})
it('creates, updates and deletes banned words with confirmed writes; duplicate keeps editor open', async () => {
  const writes: { method: string; body?: unknown }[] = []
  let duplicate = true
  server.use(
    http.post('*/api/v1/admin/banned-words', async ({ request }) => {
      writes.push({ method: 'POST', body: await request.json() })
      return duplicate
        ? HttpResponse.json({ code: 'BANNED_WORD_ALREADY_EXISTS' }, { status: 409 })
        : HttpResponse.json(words.items[0], { status: 201 })
    }),
    http.put('*/api/v1/admin/banned-words/:id', async ({ request }) => {
      writes.push({ method: 'PUT', body: await request.json() })
      return HttpResponse.json(words.items[0])
    }),
    http.delete('*/api/v1/admin/banned-words/:id', () => {
      writes.push({ method: 'DELETE' })
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderAdmin(<ModerationPage />)
  await userEvent.click(screen.getByRole('button', { name: 'Từ cấm' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Thêm từ cấm' }))
  await userEvent.type(screen.getByLabelText('Từ hoặc cụm từ'), 'spam')
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Từ này đã có')
  expect(screen.getByLabelText('Từ hoặc cụm từ')).toHaveValue('spam')
  duplicate = false
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  await screen.findByText('Đã lưu thay đổi.')
  await userEvent.click(screen.getByRole('button', { name: 'Sửa khốn nạn' }))
  await userEvent.clear(screen.getByLabelText('Từ hoặc cụm từ'))
  await userEvent.type(screen.getByLabelText('Từ hoặc cụm từ'), 'updated')
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  await screen.findByText('Đã lưu thay đổi.')
  await userEvent.click(screen.getByRole('button', { name: 'Xóa khốn nạn' }))
  expect(writes).toHaveLength(3)
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
  await screen.findByText('Đã lưu thay đổi.')
  expect(writes).toEqual([
    { method: 'POST', body: { word: 'spam' } },
    { method: 'POST', body: { word: 'spam' } },
    { method: 'PUT', body: { word: 'updated' } },
    { method: 'DELETE' },
  ])
})
it('filters schedule, searches enabled tracks and retains server locked-date errors', async () => {
  const requests: URLSearchParams[] = [],
    writes: unknown[] = []
  let fail = true
  server.use(
    http.get('*/api/v1/admin/daily-schedule', ({ request }) => {
      requests.push(new URL(request.url).searchParams)
      return HttpResponse.json(schedule)
    }),
    http.get('*/api/v1/admin/tracks', ({ request }) => {
      requests.push(new URL(request.url).searchParams)
      return HttpResponse.json(tracks)
    }),
    http.put('*/api/v1/admin/daily-schedule/:date', async ({ request, params }) => {
      writes.push({ date: params.date, body: await request.json() })
      return fail
        ? HttpResponse.json({ code: 'DAILY_CHALLENGE_LOCKED' }, { status: 409 })
        : HttpResponse.json(schedule.items[0])
    }),
  )
  renderAdmin(<ModerationPage />)
  await userEvent.click(screen.getByRole('button', { name: 'Lịch thử thách' }))
  await screen.findByText('See Tình · Hoàng Thùy Linh')
  fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-10-01' } })
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-10-31' } })
  await userEvent.click(screen.getByRole('button', { name: 'Lọc' }))
  await waitFor(() => {
    expect(requests.at(-1)?.get('from')).toBe('2026-10-01')
  })
  await userEvent.type(
    screen.getByRole('searchbox', { name: 'Tên bài hoặc ca sĩ để lên lịch' }),
    'Nơi',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Tìm' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Chọn Nơi Này Có Anh' }))
  expect(requests.at(-1)?.get('enabled')).toBe('true')
  expect(requests.at(-1)?.get('q')).toBe('Nơi')
  fireEvent.change(screen.getByLabelText('Ngày thử thách (Việt Nam)'), {
    target: { value: '2099-01-01' },
  })
  await userEvent.click(screen.getByRole('button', { name: 'Lên lịch bài' }))
  expect(writes).toEqual([])
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Không thể sửa thử thách')
  fail = false
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  await screen.findByText('Đã lưu thay đổi.')
  expect(writes[0]).toEqual({ date: '2099-01-01', body: { trackId: tracks.items[0]?.id } })
})
it('blocks active/past dates according to Vietnam time, including a UTC date boundary', async () => {
  expect(vietnamToday(new Date('2026-11-18T18:00:00Z'))).toBe('2026-11-19')
  renderAdmin(<ModerationPage />)
  await userEvent.click(screen.getByRole('button', { name: 'Lịch thử thách' }))
  fireEvent.change(screen.getByLabelText('Ngày thử thách (Việt Nam)'), {
    target: { value: '2000-01-01' },
  })
  expect(screen.getByRole('button', { name: 'Lên lịch bài' })).toBeDisabled()
  expect(screen.getByRole('alert')).toHaveTextContent('Không thể sửa thử thách')
})
it('shows reports failure, retries and displays an empty queue', async () => {
  server.use(http.get('*/api/v1/admin/reports', () => HttpResponse.json({}, { status: 500 })))
  renderAdmin(<ModerationPage />)
  await screen.findByRole('alert')
  server.use(http.get('*/api/v1/admin/reports', () => HttpResponse.json({ ...reports, items: [] })))
  await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('Không có kết quả')).toBeVisible()
})
