import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeAll, beforeEach, describe, expect, it } from 'vitest'
import analyticsExample from '../../../../../contracts/examples/admin/get-admin-analytics.200.json'
import { server } from '@/mocks/server'
import { renderAdmin } from '../shell/testing'
import { AdminDashboardPage } from './AdminDashboardPage'
import { returningShare } from './lib'
import { adminDashboardHandlers } from './mocks'

// Unit assertions cover the data; Playwright covers loading/rendering the real chart chunk.
beforeAll(async () => {
  await import('./components/DailyChart')
})

beforeEach(() => {
  server.use(...adminDashboardHandlers)
})

describe('AdminDashboardPage', () => {
  it('shows the KPI cards from the dashboard snapshot', async () => {
    renderAdmin(<AdminDashboardPage />)
    const kpi = await screen.findByRole('region', { name: 'Hôm nay' })
    expect(within(kpi).getByText('Tài khoản đã đăng ký').nextSibling).toHaveTextContent('412')
    expect(within(kpi).getByText('Trận hôm nay').nextSibling).toHaveTextContent('142')
    expect(within(kpi).getByText('14 bài đang tắt')).toBeInTheDocument()
    expect(within(kpi).getAllByText('Chưa thu thập số liệu')).toHaveLength(2)
  })

  it('shows daily analytics: KPIs and the three charts with readable summaries', async () => {
    renderAdmin(<AdminDashboardPage />)
    const section = await screen.findByRole('region', { name: 'Theo ngày' })
    expect(await within(section).findByText('36,5%')).toBeInTheDocument()
    expect(within(section).getByText('19/52 người chơi')).toBeInTheDocument()
    expect(await within(section).findByText('Người chơi mỗi ngày')).toBeInTheDocument()
    const total = analyticsExample.daily.reduce((sum, d) => sum + d.matches, 0)
    expect(
      within(section).getByText(`Tổng ${total.toLocaleString('vi')} trận từ 30-10 đến 28-11.`),
    ).toBeInTheDocument()
    expect(
      within(section).getByRole('figure', { name: 'Đã chơi từ ngày trước (%)' }),
    ).toBeInTheDocument()
  })

  it('asks the server again when another range is picked', async () => {
    const asked: string[] = []
    server.use(
      http.get('*/api/v1/admin/analytics', ({ request }) => {
        asked.push(new URL(request.url).searchParams.get('days') ?? '')
        return HttpResponse.json(analyticsExample)
      }),
    )
    renderAdmin(<AdminDashboardPage />)
    await screen.findByText('19/52 người chơi')
    await userEvent.click(screen.getByRole('button', { name: '7 ngày' }))
    expect(screen.getByRole('button', { name: '7 ngày' })).toHaveAttribute('aria-pressed', 'true')
    expect(asked).toEqual(['30', '7'])
  })

  it('marks old-period data as busy while a new range is loading', async () => {
    server.use(
      http.get('*/api/v1/admin/analytics', async ({ request }) => {
        if (new URL(request.url).searchParams.get('days') === '7') await delay(200)
        return HttpResponse.json(analyticsExample)
      }),
    )
    renderAdmin(<AdminDashboardPage />)
    await screen.findByText('19/52 người chơi')
    await userEvent.click(screen.getByRole('button', { name: '7 ngày' }))
    const region = screen.getByRole('region', { name: 'Theo ngày' })
    expect(region).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải khoảng mới')
    await waitFor(() => expect(region).toHaveAttribute('aria-busy', 'false'))
    expect(screen.queryByText(/Đang tải khoảng mới/)).not.toBeInTheDocument()
  })

  it('exposes the same daily chart values in a keyboard-accessible data table', async () => {
    renderAdmin(<AdminDashboardPage />)
    const controls = await screen.findAllByText('Xem số liệu theo ngày')
    const first = controls.at(0)
    const day = analyticsExample.daily.at(0)
    if (!first || !day) throw new Error('Missing chart fixture')
    await userEvent.click(first)
    const table = screen.getByRole('table', { name: 'Số liệu: Người chơi mỗi ngày' })
    const row = within(table).getByRole('row', { name: new RegExp(day.day) })
    expect(
      within(row)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    ).toEqual([day.activePlayers.toLocaleString('vi'), day.returningPlayers.toLocaleString('vi')])
  })

  it('says the beta KPI is unavailable when the server has no beta window', async () => {
    server.use(
      http.get('*/api/v1/admin/analytics', () =>
        HttpResponse.json({ ...analyticsExample, returningRate: null }),
      ),
    )
    renderAdmin(<AdminDashboardPage />)
    expect(await screen.findByText('Chưa đặt ngày mở beta')).toBeInTheDocument()
  })

  it('shows an error with retry when the snapshot fails', async () => {
    server.use(
      http.get('*/api/v1/admin/dashboard', () =>
        HttpResponse.json({ code: 'INTERNAL' }, { status: 500 }),
      ),
    )
    renderAdmin(<AdminDashboardPage />)
    expect(await screen.findByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
  })

  it('computes the returning share per day and leaves empty days blank', () => {
    expect(
      returningShare({ day: '2026-11-01', activePlayers: 4, returningPlayers: 1, matches: 0 }),
    ).toBe(25)
    expect(
      returningShare({ day: '2026-11-02', activePlayers: 0, returningPlayers: 0, matches: 0 }),
    ).toBeNull()
  })
})
