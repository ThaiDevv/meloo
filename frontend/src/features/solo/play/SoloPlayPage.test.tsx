import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { Link, Outlet, RouterProvider, createMemoryRouter } from 'react-router-dom'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { apiClient } from '@/shared/api/client'
import { useSessionStore } from '@/shared/session/store'
import { useToastStore } from '@/shared/ui/toast-store'
import correctExample from '../../../../../contracts/examples/game/submit-guess.200.correct.json'
import matchExample from '../../../../../contracts/examples/game/create-solo-match.201.json'
import resultExample from '../../../../../contracts/examples/game/get-match-result.200.json'
import { resetSoloPlayMock } from './mocks/handlers'
import { SoloPlayPage } from './SoloPlayPage'

vi.mock('@/features/game-core', async (importOriginal) => {
  const { StubGameRound } = await import('./testing/StubGameRound')
  return { ...(await importOriginal<object>()), GameRound: StubGameRound }
})

// Answers of the mock server, round by round.
const ANSWERS = [
  '3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21',
  'c47b0e95-1d2a-4f36-8b59-a0e7d3c61f84',
  '8d2e5b73-6c14-4a90-b7f2-1e3c9a5d0f48',
  '92d5a0e7-3f68-4b1c-8e47-d1c9b6a2f503',
  '6b0e4f93-a1d7-4c25-9f80-27e5c8d3b1a6',
] as const
const DECOY = '5b1d7e22-3c8e-4f69-82d4-7e1b9a5c8f32'
const problem = { 'Content-Type': 'application/problem+json' }

function Layout() {
  return (
    <>
      <Link to="/">Trang chủ</Link>
      <Outlet />
    </>
  )
}

function renderAt(path: string) {
  const router = createMemoryRouter(
    [
      {
        element: <Layout />,
        children: [
          { path: '/', element: <p>home page</p> },
          { path: '/solo', element: <p>setup page</p> },
          { path: '/start', element: <p>start page</p> },
          { path: '/solo/:matchId', element: <SoloPlayPage /> },
        ],
      },
    ],
    { initialEntries: [path] },
  )
  render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nextProvider>,
  )
  return router
}

async function createMatch(mode: 'RANKED' | 'PRACTICE' = 'RANKED', roundCount: 5 | 10 = 5) {
  const { data } = await apiClient.POST('/api/v1/solo/matches', { body: { mode, roundCount } })
  if (!data) throw new Error('create failed')
  return data.matchId
}

async function guess(user: UserEvent, trackId: string) {
  const input = screen.getByLabelText('stub-track')
  await user.clear(input)
  await user.click(input)
  await user.paste(trackId)
  await user.click(screen.getByRole('button', { name: 'stub-guess' }))
}

async function listen(user: UserEvent, times: number) {
  for (let i = 0; i < times; i++) {
    const before = screen.getByTestId('round').textContent
    await user.click(screen.getByRole('button', { name: 'stub-listen' }))
    await waitFor(() => {
      expect(screen.getByTestId('round').textContent).not.toBe(before)
    })
  }
}

async function next(user: UserEvent) {
  await user.click(await screen.findByRole('button', { name: /^stub-(next|finish)$/ }))
}

beforeAll(() => {
  // jsdom has no modal <dialog>; mimic what the browser does with the `open` attribute.
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
})
beforeEach(() => {
  resetSoloPlayMock()
  useSessionStore.getState().setRole('USER')
})
afterEach(() => {
  vi.unstubAllGlobals()
  useToastStore.setState({ toasts: [] })
})

describe('SoloPlayPage', () => {
  it('plays a ranked match from the intro to the summary, every figure from the server', async () => {
    const user = userEvent.setup()
    const matchId = await createMatch('RANKED', 5)
    renderAt(`/solo/${matchId}`)

    expect(await screen.findByRole('heading', { level: 1, name: 'Trận xếp hạng' })).toBeVisible()
    expect(screen.getByRole('note')).toHaveTextContent('Trận này tính xếp hạng')
    expect(screen.getByText('5 vòng · Bộ chuẩn')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Vào vòng 1' }))

    // Rounds 1–3 right at 2s: 1.000, then +10%, then +20% (BR-08).
    for (const answer of ANSWERS.slice(0, 3)) {
      await guess(user, answer)
      await screen.findByTestId('result')
      await next(user)
    }
    // Round 4: two more levels, then right at 10s.
    await listen(user, 2)
    await guess(user, ANSWERS[3])
    expect(await screen.findByTestId('result')).toHaveTextContent('CORRECT 600')
    await next(user)
    // Round 5: a wrong guess, every level, then skip.
    await guess(user, DECOY)
    expect(await screen.findByTestId('verdict')).toHaveTextContent('false')
    await listen(user, 3)
    await user.click(screen.getByRole('button', { name: 'stub-skip' }))
    expect(await screen.findByTestId('result')).toHaveTextContent('GAVE_UP 0')
    await next(user)

    expect(await screen.findByRole('heading', { level: 1, name: 'Tổng kết trận' })).toBeVisible()
    expect(screen.getByTestId('match-total')).toHaveTextContent('3.900')
    expect(screen.getByText('Đúng 4/5 bài')).toBeInTheDocument()
    expect(screen.getByText('Combo dài nhất 3 vòng')).toBeInTheDocument()
    expect(screen.getByText('Đã tính vào xếp hạng')).toBeInTheDocument()

    const items = screen.getAllByRole('listitem').filter((li) => li.textContent.includes('Vòng'))
    expect(items).toHaveLength(5)
    expect(items[1]).toHaveTextContent('+10% combo')
    expect(items[2]).toHaveTextContent('+20% combo')
    expect(items[3]).toHaveTextContent('Đúng ở 10s')
    expect(items[4]).toHaveTextContent('Bỏ qua')
    expect(items[2]).toHaveTextContent('1.200')
    expect(screen.getByRole('button', { name: 'Chơi lại' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Chia sẻ' })).toBeEnabled()
  }, 15_000)

  it('shows exactly what the server says, not a score of its own', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('*/api/v1/solo/matches/active', () => HttpResponse.json(matchExample)),
      http.post('*/api/v1/solo/matches/:matchId/rounds/:roundNo/guesses', () =>
        HttpResponse.json(correctExample),
      ),
    )
    renderAt(`/solo/${matchExample.matchId}`)
    await user.click(await screen.findByRole('button', { name: 'Vào vòng 1' }))
    await guess(user, 'any-track')
    // correctExample: right at 5s after one wrong guess → 800, total 800, streak 1.
    expect(await screen.findByTestId('result')).toHaveTextContent('CORRECT 800')
    expect(screen.getByTestId('round')).toHaveTextContent('total 800 combo 1')
  })

  it('offers catalog suggestions as the player types', async () => {
    const user = userEvent.setup()
    renderAt(`/solo/${await createMatch()}`)
    await user.click(await screen.findByRole('button', { name: 'Vào vòng 1' }))
    await user.type(screen.getByLabelText('stub-track'), 'em la')
    await waitFor(() => {
      expect(screen.getByTestId('suggestions')).toHaveTextContent('Em Là Của Anh')
    })
  })

  it('warns before a practice match that it is not ranked (BR-23)', async () => {
    renderAt(`/solo/${await createMatch('PRACTICE', 10)}`)
    expect(await screen.findByRole('heading', { level: 1, name: 'Trận luyện tập' })).toBeVisible()
    expect(screen.getByRole('note')).toHaveTextContent('Trận này không tính xếp hạng')
    expect(screen.getByText('10 vòng · Toàn bộ kho nhạc')).toBeInTheDocument()
  })

  it('tells a guest that a ranked score reaches the board after signing in (BR-20)', async () => {
    useSessionStore.getState().setRole('GUEST')
    renderAt(`/solo/${await createMatch()}`)
    expect(await screen.findByRole('note')).toHaveTextContent('Khi đăng nhập')
  })

  it('resumes a match at the round the server kept', async () => {
    const user = userEvent.setup()
    const matchId = await createMatch()
    await apiClient.POST('/api/v1/solo/matches/{matchId}/rounds/{roundNo}/guesses', {
      params: { path: { matchId, roundNo: 1 } },
      body: { trackId: ANSWERS[0] },
    })
    renderAt(`/solo/${matchId}`)
    expect(await screen.findByText('Đang ở vòng 2 / 5 · 1.000 điểm')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Chơi tiếp vòng 2' }))
    expect(screen.getByTestId('round')).toHaveTextContent('round 2/5 level 1 total 1000')
  })

  it('asks before leaving a ranked match and leaves without abandoning it', async () => {
    const user = userEvent.setup()
    const abandon = vi.fn()
    server.use(
      http.post('*/api/v1/solo/matches/:matchId/abandon', () => {
        abandon()
        return HttpResponse.json({}, { status: 500 })
      }),
    )
    const router = renderAt(`/solo/${await createMatch()}`)
    await user.click(await screen.findByRole('button', { name: 'Vào vòng 1' }))
    await user.click(screen.getByRole('button', { name: 'Thoát' }))

    const dialog = screen.getByRole('dialog', { name: 'Rời trận xếp hạng?' })
    expect(dialog).toHaveTextContent('30 phút')
    await user.click(within(dialog).getByRole('button', { name: 'Ở lại chơi' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Thoát' }))
    await user.click(screen.getByRole('button', { name: 'Rời trận' }))
    expect(await screen.findByText('home page')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(abandon).not.toHaveBeenCalled()
  })

  it('asks before a link in the app takes the player away mid-match', async () => {
    const user = userEvent.setup()
    const router = renderAt(`/solo/${await createMatch()}`)
    await user.click(await screen.findByRole('button', { name: 'Vào vòng 1' }))
    await user.click(screen.getByRole('link', { name: 'Trang chủ' }))

    expect(await screen.findByRole('dialog', { name: 'Rời trận xếp hạng?' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ở lại chơi' }))
    expect(router.state.location.pathname).toMatch(/^\/solo\//)
    expect(screen.getByTestId('game-round')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Trang chủ' }))
    await user.click(await screen.findByRole('button', { name: 'Rời trận' }))
    expect(await screen.findByText('home page')).toBeInTheDocument()
  })

  it('abandons a practice match after confirmation and shows what was played', async () => {
    const user = userEvent.setup()
    renderAt(`/solo/${await createMatch('PRACTICE', 5)}`)
    await user.click(await screen.findByRole('button', { name: 'Vào vòng 1' }))
    await guess(user, ANSWERS[0])
    await next(user)
    await user.click(screen.getByRole('button', { name: 'Thoát' }))

    const dialog = screen.getByRole('dialog', { name: 'Bỏ trận luyện tập?' })
    await user.click(within(dialog).getByRole('button', { name: 'Bỏ trận' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Trận đã dừng' })).toBeVisible()
    expect(screen.getByText('Dừng sau 1/5 vòng, không tính xếp hạng')).toBeInTheDocument()
    expect(screen.getByTestId('match-total')).toHaveTextContent('1.000')
  })

  it('plays again with the same settings in a fresh match', async () => {
    const user = userEvent.setup()
    const router = renderAt(`/solo/${await createMatch('RANKED', 5)}`)
    await user.click(await screen.findByRole('button', { name: 'Vào vòng 1' }))
    for (const answer of ANSWERS) {
      await guess(user, answer)
      await next(user)
    }
    const first = router.state.location.pathname
    await user.click(await screen.findByRole('button', { name: 'Chơi lại' }))

    expect(await screen.findByRole('button', { name: 'Vào vòng 1' })).toBeInTheDocument()
    expect(router.state.location.pathname).not.toBe(first)
    expect(screen.getByText('5 vòng · Bộ chuẩn')).toBeInTheDocument()
  }, 15_000)

  it('shares the result without song titles, by clipboard when there is no share sheet', async () => {
    const user = userEvent.setup()
    const writeText = vi.fn().mockResolvedValue(undefined)
    server.use(
      http.get('*/api/v1/solo/matches/active', () =>
        HttpResponse.json({ code: 'NO_ACTIVE_MATCH' }, { status: 404, headers: problem }),
      ),
      http.get('*/api/v1/solo/matches/:matchId/result', () => HttpResponse.json(resultExample)),
    )
    renderAt(`/solo/${resultExample.matchId}`)
    await screen.findByRole('heading', { level: 1, name: 'Tổng kết trận' })
    vi.stubGlobal('navigator', { userAgent: navigator.userAgent, clipboard: { writeText } })
    await user.click(screen.getByRole('button', { name: 'Chia sẻ' }))

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledOnce()
    })
    const text = String(writeText.mock.calls[0]?.[0])
    expect(text).toContain('3.500 điểm · đúng 4/5 bài · combo dài nhất 2')
    expect(text).not.toContain('Waiting For You')
    expect(useToastStore.getState().toasts.at(-1)?.title).toBe(
      'Đã chép kết quả. Dán vào đâu cũng được.',
    )
  })

  it('points to the other match when this one is not the player’s match in progress', async () => {
    const activeId = await createMatch()
    renderAt('/solo/0f6e1b92-0000-4000-8000-000000000000')
    expect(await screen.findByText('Bạn đang có một trận khác chưa xong')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Chơi tiếp trận đó' })).toHaveAttribute(
      'href',
      `/solo/${activeId}`,
    )
  })

  it('says when the match does not exist', async () => {
    renderAt('/solo/abc')
    expect(await screen.findByText('Không tìm thấy trận này')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Chọn trận mới' })).toHaveAttribute('href', '/solo')
  })

  it('sends a signed-out visitor to /start', async () => {
    server.use(
      http.get('*/api/v1/solo/matches/active', () =>
        HttpResponse.json({ code: 'UNAUTHORIZED' }, { status: 401, headers: problem }),
      ),
    )
    renderAt('/solo/abc')
    expect(await screen.findByText('start page')).toBeInTheDocument()
  })

  it('shows an error with a retry when the match cannot be loaded', async () => {
    const user = userEvent.setup()
    let fail = true
    server.use(
      http.get('*/api/v1/solo/matches/active', () =>
        fail ? HttpResponse.json({}, { status: 503 }) : HttpResponse.json(matchExample),
      ),
    )
    renderAt(`/solo/${matchExample.matchId}`)
    expect(await screen.findByText('Không mở được trận')).toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('button', { name: 'Vào vòng 1' })).toBeInTheDocument()
  })
})
