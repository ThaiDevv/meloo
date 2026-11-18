import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import analyticsExample from '../../../../../contracts/examples/admin/get-admin-analytics.200.json'
import { server } from '@/mocks/server'
import { renderAdmin } from '../shell/testing'
import { AdminDashboardPage } from './AdminDashboardPage'
import { returningShare } from './lib'
import { adminDashboardHandlers } from './mocks'

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
    expect(within(section).getByText('Tỉ lệ người chơi quay lại')).toBeInTheDocument()
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
