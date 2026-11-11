import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import detailExample from '../../../../contracts/examples/progression/get-my-match-detail.200.json'
import { profileHandlers } from './mocks'
import { MatchDetailPage } from './MatchDetailPage'

const QUICK_MATCH_ID = detailExample.matchId
const SOLO_MATCH_ID = '4c9a2e10-7d5b-4a81-8c32-1e7f9a0b4d22'

function renderPage(matchId: string) {
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={[`/profile/matches/${matchId}`]}>
          <Routes>
            <Route path="/profile/matches/:matchId" element={<MatchDetailPage />} />
            <Route path="/profile" element={<p>profile page</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

describe('MatchDetailPage', () => {
  it('shows every round with its song, correct level and points (AC2)', async () => {
    server.use(...profileHandlers)
    renderPage(QUICK_MATCH_ID)

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Ghép trận nhanh' }),
    ).toBeInTheDocument()
    const rounds = screen.getAllByTestId('round-row')
    expect(rounds).toHaveLength(2)

    const [first, second] = rounds
    if (!first || !second) throw new Error('missing rounds')
    expect(within(first).getByText('Vòng 1')).toBeInTheDocument()
    expect(within(first).getByText('Nơi Này Có Anh')).toBeInTheDocument()
    expect(within(first).getByText('Sơn Tùng M-TP')).toBeInTheDocument()
    expect(within(first).getByText('Đúng ở mức 1 (2 giây)')).toBeInTheDocument()
    expect(first).toHaveTextContent('1.180 điểm')

    expect(within(second).getByText('See Tình')).toBeInTheDocument()
    expect(within(second).getByText('Đúng ở mức 2 (5 giây)')).toBeInTheDocument()
    expect(within(second).getByText('Sai 1 lần')).toBeInTheDocument()
    expect(second).toHaveTextContent('940 điểm')
    expect(within(second).getByRole('link', { name: /Nghe cả bài/ })).toHaveAttribute(
      'href',
      'https://www.deezer.com/track/350123452',
    )
  })

  it('shows the match total, placement and rank change', async () => {
    server.use(...profileHandlers)
    renderPage(QUICK_MATCH_ID)

    const heading = await screen.findByRole('heading', { level: 1 })
    expect(heading.parentElement).toHaveTextContent('7.420điểm')
    expect(screen.getByText('Đúng 8 / 10 vòng · Hạng 1 / 4')).toBeInTheDocument()
    expect(screen.getByText('Tính hạng')).toBeInTheDocument()
  })

  it('lists the final standings of a room match and marks the caller', async () => {
    server.use(...profileHandlers)
    renderPage(QUICK_MATCH_ID)

    const heading = await screen.findByRole('heading', { level: 2, name: 'Bảng xếp hạng trận' })
    const list = heading.nextElementSibling
    if (!(list instanceof HTMLElement)) throw new Error('no standings')
    const rows = within(list).getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    // The caller is known once the profile lands.
    await within(list).findByText(/· bạn/)
    expect(rows[0]).toHaveTextContent('MinhNhac · bạn')
    expect(rows[0]).toHaveTextContent('7.420')
    expect(rows[1]).toHaveTextContent('LanAnh')
    expect(rows[1]).not.toHaveTextContent('bạn')
    expect(rows[1]).toHaveTextContent('+10 điểm hạng')
  })

  it('has no standings for a solo match', async () => {
    server.use(...profileHandlers)
    renderPage(SOLO_MATCH_ID)

    expect(await screen.findByRole('heading', { level: 1, name: 'Chơi đơn' })).toBeInTheDocument()
    expect(screen.queryByText('Bảng xếp hạng trận')).not.toBeInTheDocument()
    expect(screen.queryByText(/Hạng 1 \/ 1/)).not.toBeInTheDocument()
  })

  it('names rounds that were not solved by their outcome', async () => {
    const [firstRound] = detailExample.rounds
    server.use(
      http.get('*/api/v1/me/matches/:matchId', () =>
        HttpResponse.json({
          ...detailExample,
          status: 'ABANDONED',
          rounds: [
            {
              ...firstRound,
              outcome: 'GAVE_UP',
              correctLevel: null,
              listenSeconds: null,
              baseScore: 0,
              speedBonus: 0,
              roundScore: 0,
              answerTimeMs: null,
            },
          ],
        }),
      ),
      ...profileHandlers,
    )
    renderPage(QUICK_MATCH_ID)

    const row = await screen.findByTestId('round-row')
    expect(within(row).getByText('Bỏ qua')).toBeInTheDocument()
    expect(row).toHaveTextContent('0 điểm')
    expect(screen.getByText('Bỏ dở')).toBeInTheDocument()
  })

  it('says so when the match is not found or not the caller’s (AC3)', async () => {
    const user = userEvent.setup()
    server.use(...profileHandlers)
    renderPage('11111111-1111-4111-8111-999999999999')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Không tìm thấy trận này' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    const backLinks = screen.getAllByRole('link', { name: 'Về hồ sơ' })
    const back = backLinks.at(-1)
    if (!back) throw new Error('no back link')
    await user.click(back)
    expect(await screen.findByText('profile page')).toBeInTheDocument()
  })

  it('shows loading, then an error with retry on a server failure (AC3)', async () => {
    const user = userEvent.setup()
    let calls = 0
    server.use(
      http.get('*/api/v1/me/matches/:matchId', async () => {
        calls += 1
        await delay(20)
        return calls === 1
          ? HttpResponse.json({}, { status: 500 })
          : HttpResponse.json(detailExample)
      }),
      ...profileHandlers,
    )
    const { container } = renderPage(QUICK_MATCH_ID)

    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull()
    expect(screen.getByText('Đang tải…')).toBeInTheDocument()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Không tải được chi tiết trận. Hãy thử lại.')
    await user.click(within(alert).getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Nơi Này Có Anh')).toBeInTheDocument()
  })
})
