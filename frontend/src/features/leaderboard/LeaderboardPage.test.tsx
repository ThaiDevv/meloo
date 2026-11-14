import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useSessionStore, type Role } from '@/shared/session/store'
import dailyExample from '../../../../contracts/examples/daily/get-daily-leaderboard.200.json'
import allTimeExample from '../../../../contracts/examples/progression/get-all-time-leaderboard.200.json'
import { LeaderboardPage } from './LeaderboardPage'
import { leaderboardHandlers } from './mocks'

function renderPage(role: Role | null) {
  useSessionStore.getState().setRole(role)
  render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={['/leaderboard']}>
          <LeaderboardPage />
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

beforeEach(() => {
  server.use(...leaderboardHandlers)
})
afterEach(() => {
  useSessionStore.getState().setRole(null)
})

describe('LeaderboardPage', () => {
  it('shows exactly two tabs, all-time and today, with no weekly board', () => {
    renderPage('USER')
    const tabs = within(screen.getByRole('tablist')).getAllByRole('tab')
    expect(tabs.map((t) => t.textContent)).toEqual(['Toàn thời gian', 'Hôm nay'])
    expect(screen.getByRole('tab', { name: 'Toàn thời gian' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('lists the all-time board by rank points in server order and highlights my row', async () => {
    renderPage('USER')
    const list = await screen.findByRole('list', { name: 'Toàn thời gian' })
    const rows = within(list).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('NhacThanh')
    expect(rows[0]).toHaveTextContent('1.820 điểm rank')
    expect(rows[1]).toHaveTextContent('MinhNhac')
    expect(rows[1]).toHaveAttribute('aria-current', 'true')
    expect(rows[1]).toHaveTextContent('Bạn')
    expect(rows[0]).not.toHaveAttribute('aria-current')
    // My entry is on this page, so no separate "your position" block.
    expect(screen.queryByText('Vị trí của bạn')).not.toBeInTheDocument()
  })

  it('renders a saved preset avatar returned by the leaderboard', async () => {
    server.use(
      http.get('*/api/v1/leaderboards/all-time', () =>
        HttpResponse.json({
          ...allTimeExample,
          items: [{ ...allTimeExample.items[0], avatarId: 'piano' }],
          myEntry: null,
        }),
      ),
    )
    renderPage('USER')
    const list = await screen.findByRole('list', { name: 'Toàn thời gian' })
    expect(list.querySelector('[data-avatar-id="piano"]')).toBeInTheDocument()
  })

  it('pins my position above the list when I am not on the current page', async () => {
    server.use(
      http.get('*/api/v1/leaderboards/all-time', () =>
        HttpResponse.json({
          ...allTimeExample,
          items: allTimeExample.items.slice(0, 1),
          myEntry: { ...allTimeExample.myEntry, rank: 57 },
        }),
      ),
    )
    renderPage('USER')
    const mine = await screen.findByTestId('leaderboard-row-me')
    expect(mine).toHaveTextContent('57')
    expect(mine).toHaveTextContent('MinhNhac')
    expect(screen.getByText('Vị trí của bạn')).toBeInTheDocument()
  })

  it('pages through the board with page and size query params', async () => {
    const requested: string[] = []
    server.use(
      http.get('*/api/v1/leaderboards/all-time', ({ request }) => {
        const url = new URL(request.url)
        const page = Number(url.searchParams.get('page'))
        requested.push(`${String(page)}/${url.searchParams.get('size') ?? ''}`)
        return HttpResponse.json({
          ...allTimeExample,
          page,
          size: 20,
          totalItems: 25,
          totalPages: 2,
          items: [
            { ...allTimeExample.items[0], rank: page * 20 + 1, nickname: `Player${String(page)}` },
          ],
          myEntry: null,
        })
      }),
    )
    const user = userEvent.setup()
    renderPage('USER')
    expect(await screen.findByText('Player0')).toBeInTheDocument()
    expect(screen.getByText('Trang 1/2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Trang trước' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Trang sau' }))
    expect(await screen.findByText('Player1')).toBeInTheDocument()
    expect(screen.getByText('Trang 2/2')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Trang sau' })).toBeDisabled()
    })
    expect(requested).toEqual(['0/20', '1/20'])
  })

  it('shows today board ranked by guess level then time, with my row highlighted', async () => {
    const user = userEvent.setup()
    renderPage('USER')
    await user.click(screen.getByRole('tab', { name: 'Hôm nay' }))
    const list = await screen.findByRole('list', { name: 'Hôm nay' })
    const rows = within(list).getAllByRole('listitem')
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent('Đoán đúng ở 2 giây · 2,8 giây')
    expect(rows[0]).toHaveTextContent('1.000 điểm')
    expect(rows[1]).toHaveAttribute('aria-current', 'true')
    expect(rows[2]).toHaveTextContent('Chưa đoán ra · 48,2 giây')
    expect(screen.getByText(/Thử thách ngày 29 tháng 10, 2026/)).toBeInTheDocument()
  })

  it('invites a guest to sign in but still shows the boards without their name', async () => {
    server.use(
      http.get('*/api/v1/leaderboards/all-time', () =>
        HttpResponse.json({ ...allTimeExample, myEntry: null }),
      ),
    )
    renderPage('GUEST')
    expect(
      screen.getByRole('heading', { name: 'Đăng nhập để chơi xếp hạng và lên bảng' }),
    ).toBeInTheDocument()
    expect(screen.getByTestId('btn-login-google')).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'Toàn thời gian' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.queryByTestId('leaderboard-row-me')).not.toBeInTheDocument()
  })

  it('shows only the invite when there is no session yet', () => {
    renderPage(null)
    expect(screen.getByTestId('leaderboard-guest-invite')).toBeInTheDocument()
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
  })

  it('does not show the invite to a registered player', async () => {
    renderPage('USER')
    await screen.findByRole('list', { name: 'Toàn thời gian' })
    expect(screen.queryByTestId('leaderboard-guest-invite')).not.toBeInTheDocument()
  })

  it('shows an error with retry, then recovers', async () => {
    let fail = true
    server.use(
      http.get('*/api/v1/leaderboards/all-time', () =>
        fail
          ? HttpResponse.json({ status: 500 }, { status: 500 })
          : HttpResponse.json(allTimeExample),
      ),
    )
    const user = userEvent.setup()
    renderPage('USER')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('list', { name: 'Toàn thời gian' })).toBeInTheDocument()
  })

  it('shows an empty state when nobody completed today', async () => {
    server.use(
      http.get('*/api/v1/leaderboards/daily', () =>
        HttpResponse.json({
          ...dailyExample,
          items: [],
          totalItems: 0,
          totalPages: 0,
          myEntry: null,
        }),
      ),
    )
    const user = userEvent.setup()
    renderPage('USER')
    await user.click(screen.getByRole('tab', { name: 'Hôm nay' }))
    expect(await screen.findByText('Hôm nay chưa ai hoàn thành')).toBeInTheDocument()
  })

  it('renders in English', async () => {
    await i18n.changeLanguage('en')
    renderPage('GUEST')
    expect(
      screen.getByRole('heading', { name: 'Sign in to play ranked and get on the board' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'All time' })).toBeInTheDocument()
    expect(await screen.findByText('1,820 rank points')).toBeInTheDocument()
  })
})
