import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import statsExample from '../../../../contracts/examples/progression/get-my-stats.200.json'
import { profileHandlers } from './mocks'
import { ProfilePage } from './ProfilePage'

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

function renderPage(path = '/profile') {
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              path="/profile"
              element={
                <>
                  <ProfilePage />
                  <LocationProbe />
                </>
              }
            />
            <Route path="/profile/matches/:matchId" element={<p>detail page</p>} />
            <Route path="/solo" element={<p>solo page</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

const newPlayerStats = {
  matchesPlayed: 0,
  matchesWon: 0,
  winRate: 0,
  roundsPlayed: 0,
  roundsCorrect: 0,
  averageCorrectLevel: null,
  averageListenSeconds: null,
  bestCombo: 0,
  rankPoints: 0,
  xp: 0,
  favoriteGenre: null,
}

const emptyHistory = { page: 0, size: 10, totalItems: 0, totalPages: 0, items: [] }

function matchLinks() {
  return screen
    .getAllByRole('link')
    .filter((link) => link.getAttribute('href')?.startsWith('/profile/matches/'))
}

function statValue(term: string) {
  const dt = screen.getByText(term, { selector: 'dt' })
  const group = dt.parentElement
  if (!group) throw new Error(`no group for ${term}`)
  return within(group)
}

describe('ProfilePage', () => {
  it('shows the nickname, rank points and XP from the profile', async () => {
    server.use(...profileHandlers)
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'MinhNhac' })).toBeInTheDocument()
    expect(screen.getByText('1.450 điểm hạng · 3.280 XP')).toBeInTheDocument()
  })

  it('shows matches played, win rate, average correct level and best genre (AC1)', async () => {
    server.use(...profileHandlers)
    renderPage()

    await screen.findByText('Số trận', { selector: 'dt' })
    expect(statValue('Số trận').getByText('24')).toBeInTheDocument()
    expect(statValue('Số trận').getByText('Thắng 15 trận')).toBeInTheDocument()
    expect(statValue('Tỉ lệ thắng').getByText('62,5%')).toBeInTheDocument()
    expect(statValue('Mức đoán đúng trung bình').getByText('Mức 2,2')).toBeInTheDocument()
    expect(
      statValue('Mức đoán đúng trung bình').getByText('Nghe trung bình 6,4 giây'),
    ).toBeInTheDocument()
    expect(statValue('Thể loại sở trường').getByText('Nhạc trẻ Việt Nam')).toBeInTheDocument()
  })

  it('names the genre in English when the language is English', async () => {
    await i18n.changeLanguage('en')
    server.use(...profileHandlers)
    renderPage()

    await screen.findByText('Best genre', { selector: 'dt' })
    expect(statValue('Best genre').getByText('V-Pop')).toBeInTheDocument()
    expect(statValue('Win rate').getByText('62.5%')).toBeInTheDocument()
  })

  it('lists matches newest first and links each one to its detail (AC2)', async () => {
    const user = userEvent.setup()
    server.use(...profileHandlers)
    renderPage()

    await screen.findByText('Trang 1 / 2')
    const rows = matchLinks()
    expect(rows).toHaveLength(10)
    const first = rows[0]
    if (!first) throw new Error('no first row')
    expect(first).toHaveTextContent('Ghép trận nhanh')
    expect(first).toHaveTextContent('Tính hạng')
    expect(first).toHaveTextContent('Đúng 8 / 10 vòng · Hạng 1 / 4')
    expect(first).toHaveTextContent('7.420')
    expect(first).toHaveTextContent('+30 điểm hạng')
    expect(rows[1]).toHaveTextContent('Chơi đơn')
    expect(rows[1]).not.toHaveTextContent('Hạng 1 / 1')

    await user.click(first)
    expect(await screen.findByText('detail page')).toBeInTheDocument()
  })

  it('pages through the history and keeps the page in the URL (AC2)', async () => {
    const user = userEvent.setup()
    const pagesAsked: string[] = []
    server.use(
      http.get('*/api/v1/me/matches', ({ request }) => {
        pagesAsked.push(new URL(request.url).search)
        return undefined
      }),
      ...profileHandlers,
    )
    renderPage()

    expect(await screen.findByText('Trang 1 / 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Trang trước/ })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /Trang sau/ }))
    expect(await screen.findByText('Trang 2 / 2')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/profile?page=2')
    expect(screen.getByRole('button', { name: /Trang sau/ })).toBeDisabled()
    expect(matchLinks()).toHaveLength(3)
    expect(pagesAsked).toEqual(['?page=0&size=10', '?page=1&size=10'])

    await user.click(screen.getByRole('button', { name: /Trang trước/ }))
    expect(await screen.findByText('Trang 1 / 2')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/profile$/)
  })

  it('opens the page given in the URL', async () => {
    server.use(...profileHandlers)
    renderPage('/profile?page=2')
    expect(await screen.findByText('Trang 2 / 2')).toBeInTheDocument()
  })

  it('offers the first page when the URL points past the end', async () => {
    const user = userEvent.setup()
    server.use(...profileHandlers)
    renderPage('/profile?page=9')

    expect(await screen.findByText('Trang này không còn trận nào.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Về trang đầu' }))
    expect(await screen.findByText('Trang 1 / 2')).toBeInTheDocument()
  })

  it('greets a new player with empty states and a way to start (AC3)', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/v1/me/stats', () => HttpResponse.json(newPlayerStats)),
      http.get('*/api/v1/me/matches', () => HttpResponse.json(emptyHistory)),
      ...profileHandlers,
    )
    renderPage()

    expect(await screen.findByText('Chưa có số liệu')).toBeInTheDocument()
    expect(await screen.findByText('Bạn chưa chơi trận nào')).toBeInTheDocument()
    expect(screen.queryByText('Số trận', { selector: 'dt' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Chơi trận đầu tiên' }))
    expect(await screen.findByText('solo page')).toBeInTheDocument()
  })

  it('shows "none yet" for averages and genre when there is no correct round', async () => {
    server.use(
      http.get('*/api/v1/me/stats', () =>
        HttpResponse.json({ ...newPlayerStats, matchesPlayed: 1, roundsPlayed: 5 }),
      ),
      ...profileHandlers,
    )
    renderPage()

    await screen.findByText('Mức đoán đúng trung bình', { selector: 'dt' })
    expect(statValue('Mức đoán đúng trung bình').getByText('Chưa có')).toBeInTheDocument()
    expect(
      statValue('Mức đoán đúng trung bình').getByText('Chưa có vòng đoán đúng'),
    ).toBeInTheDocument()
    expect(statValue('Thể loại sở trường').getByText('Chưa có')).toBeInTheDocument()
  })

  it('marks each section busy while it loads (AC3)', () => {
    server.use(
      http.get('*/api/v1/me/*', async () => {
        await delay('infinite')
        return HttpResponse.json({})
      }),
    )
    const { container } = renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Hồ sơ' })).toBeInTheDocument()
    expect(container.querySelectorAll('[aria-busy="true"]')).toHaveLength(3)
    expect(screen.getAllByText('Đang tải…')).toHaveLength(2)
  })

  it('shows an error with retry per section, and recovers on retry (AC3)', async () => {
    const user = userEvent.setup()
    let statsCalls = 0
    server.use(
      http.get('*/api/v1/me/stats', () => {
        statsCalls += 1
        return statsCalls === 1
          ? HttpResponse.json({}, { status: 500 })
          : HttpResponse.json(statsExample)
      }),
      http.get('*/api/v1/me/matches', () => HttpResponse.json({}, { status: 503 })),
      ...profileHandlers,
    )
    renderPage()

    expect(await screen.findByText('Không tải được thống kê. Hãy thử lại.')).toBeInTheDocument()
    expect(await screen.findByText('Không tải được lịch sử trận. Hãy thử lại.')).toBeInTheDocument()
    expect(screen.getAllByRole('alert')).toHaveLength(2)
    // The profile header still loads on its own.
    expect(screen.getByRole('heading', { level: 1, name: 'MinhNhac' })).toBeInTheDocument()

    const statsAlert = screen
      .getByText('Không tải được thống kê. Hãy thử lại.')
      .closest('[role="alert"]')
    if (!(statsAlert instanceof HTMLElement)) throw new Error('no stats alert')
    await user.click(within(statsAlert).getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Thắng 15 trận')).toBeInTheDocument()
  })

  it('shows the profile error with retry when the profile fails', async () => {
    server.use(
      http.get('*/api/v1/me/profile', () => HttpResponse.json({}, { status: 500 })),
      ...profileHandlers,
    )
    renderPage()

    expect(await screen.findByText('Không tải được hồ sơ. Hãy thử lại.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Hồ sơ' })).toBeInTheDocument()
  })
})
