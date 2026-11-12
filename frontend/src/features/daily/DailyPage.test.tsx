import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useAuthStore } from '@/shared/api/authStore'
import { apiClient } from '@/shared/api/client'
import { useToastStore } from '@/shared/ui/toast-store'
import notStarted from '../../../../contracts/examples/daily/get-daily-today.not-started.200.json'
import solved from '../../../../contracts/examples/daily/get-daily-today.solved.200.json'
import { DailyPage } from './DailyPage'
import { resetDailyMock } from './mocks'

vi.mock('@/features/game-core', async (importOriginal) => {
  const { StubGameRound } = await import('./testing/StubGameRound')
  return { ...(await importOriginal<object>()), GameRound: StubGameRound }
})

// The track the mock server counts as right (the solo mock catalog has it too).
const ANSWER_ID = '3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21'
const WRONG_ID = '5b1d7e22-3c8e-4f69-82d4-7e1b9a5c8f32'

function renderPage() {
  const router = createMemoryRouter(
    [
      { path: '/daily', element: <DailyPage /> },
      { path: '/start', element: <p>start page</p> },
    ],
    { initialEntries: ['/daily'] },
  )
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nextProvider>,
  )
  return Object.assign(router, { client })
}

function signInAs(id: string) {
  act(() => {
    useAuthStore.setState({
      accessToken: 'token',
      user: { id, kind: 'GUEST', role: 'USER', nickname: id, createdAt: '2026-10-29T00:00:00Z' },
    })
  })
}

async function typeAndGuess(user: ReturnType<typeof userEvent.setup>, trackId: string) {
  const input = screen.getByLabelText('stub-track')
  await user.clear(input)
  await user.click(input)
  await user.paste(trackId)
  await user.click(screen.getByRole('button', { name: 'stub-guess' }))
}

beforeEach(() => {
  resetDailyMock()
  useToastStore.setState({ toasts: [] })
})
afterEach(() => {
  cleanup()
  useAuthStore.getState().clear()
  vi.unstubAllGlobals()
})

describe('DailyPage: before the attempt', () => {
  it('shows the streak the server counted and a start button', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Thử thách ngày' })).toBeVisible()
    const streak = screen.getByTestId('daily-streak')
    expect(streak).toHaveTextContent('Chuỗi hiện tại')
    expect(streak).toHaveTextContent('3 ngày')
    expect(streak).toHaveTextContent('5 ngày')
    expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeEnabled()
  })

  it('shows a skeleton while loading', () => {
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải thử thách…')
  })

  it('offers a retry when loading fails', async () => {
    let fail = true
    server.use(
      http.get('*/api/v1/daily/today', () =>
        fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(notStarted),
      ),
    )
    renderPage()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Chưa tải được thử thách ngày')
    fail = false
    await userEvent.setup().click(within(alert).getByRole('button'))
    expect(await screen.findByRole('button', { name: 'Bắt đầu' })).toBeVisible()
  })

  it('says so when no song is scheduled', async () => {
    server.use(
      http.get('*/api/v1/daily/today', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Not found',
            status: 404,
            code: 'DAILY_CHALLENGE_NOT_AVAILABLE',
          },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderPage()
    expect(await screen.findByText('Hôm nay chưa có bài')).toBeVisible()
  })

  it('sends a signed-out player to sign in', async () => {
    server.use(http.get('*/api/v1/daily/today', () => new HttpResponse(null, { status: 401 })))
    const router = renderPage()
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/start')
    })
  })

  it('keeps a forbidden guest on the error screen instead of redirecting to start', async () => {
    signInAs('forbidden-guest')
    let calls = 0
    server.use(
      http.get('*/api/v1/daily/today', () => {
        calls += 1
        return HttpResponse.json({ code: 'FORBIDDEN' }, { status: 403 })
      }),
    )
    const router = renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa tải được thử thách ngày')
    expect(router.state.location.pathname).toBe('/daily')
    expect(useAuthStore.getState().user?.id).toBe('forbidden-guest')
    expect(calls).toBe(1)

    server.use(http.get('*/api/v1/daily/today', () => HttpResponse.json(notStarted)))
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('button', { name: 'Bắt đầu' })).toBeVisible()
  })

  it('keeps the session and offers retry when token refresh is temporarily unavailable', async () => {
    signInAs('refresh-unavailable')
    server.use(
      http.get('*/api/v1/daily/today', () => new HttpResponse(null, { status: 401 })),
      http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 503 })),
    )
    const router = renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa tải được thử thách ngày')
    expect(router.state.location.pathname).toBe('/daily')
    expect(useAuthStore.getState().user?.id).toBe('refresh-unavailable')
  })
})

describe('DailyPage: playing the one attempt', () => {
  it('keeps the intro when starting is forbidden without redirecting a valid guest', async () => {
    signInAs('forbidden-start')
    server.use(
      http.post('*/api/v1/daily/today/start', () =>
        HttpResponse.json({ code: 'FORBIDDEN' }, { status: 403 }),
      ),
    )
    const router = renderPage()
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Bắt đầu' }))

    await waitFor(() => {
      expect(useToastStore.getState().toasts.at(-1)?.tone).toBe('negative')
    })
    expect(router.state.location.pathname).toBe('/daily')
    expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeEnabled()
  })
  it('plays the song of the day with game-core: listen more, guess wrong, guess right', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Bắt đầu' }))

    expect(await screen.findByTestId('round')).toHaveTextContent('level 1')
    // The 5-minute ceiling is the server's `expiresAt`.
    expect(screen.getByTestId('timer').textContent).toMatch(/^\d{4}-\d{2}-\d{2}T/)

    await user.click(screen.getByRole('button', { name: 'stub-listen' }))
    expect(await screen.findByText('level 2')).toBeVisible()

    await typeAndGuess(user, WRONG_ID)
    expect(await screen.findByText('level 3')).toBeVisible()
    expect(screen.getByTestId('verdict')).toHaveTextContent('false')

    await typeAndGuess(user, ANSWER_ID)
    expect(await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })).toBeVisible()
  })

  it('shows the answer, the streak from the server, the rank and the countdown after a right guess', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await screen.findByTestId('round')
    await typeAndGuess(user, ANSWER_ID)

    await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })
    expect(screen.getByText('Chúng Ta Của Hiện Tại')).toBeVisible()
    // 3 days before, one more now: the server counted it.
    expect(screen.getByTestId('daily-streak')).toHaveTextContent('4 ngày')
    expect(screen.getByText('Hạng hôm nay: 12')).toBeVisible()
    expect(screen.getByTestId('daily-countdown')).toHaveTextContent('Bài mới sau')
    expect(screen.getByTestId('daily-countdown').querySelector('time')?.textContent).toMatch(
      /^\d{2}:\d{2}:\d{2}$/,
    )
  })

  it('ends as not guessed after a skip at the 30 second level', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    for (let level = 2; level <= 5; level++) {
      await user.click(await screen.findByRole('button', { name: 'stub-listen' }))
      await screen.findByText(`level ${String(level)}`)
    }
    await user.click(screen.getByRole('button', { name: 'stub-skip' }))

    await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })
    expect(screen.getByTestId('daily-streak')).toHaveTextContent('0 ngày')
  })

  it('takes the server view when the clock runs out', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await screen.findByTestId('round')
    server.use(http.get('*/api/v1/daily/today', () => HttpResponse.json(solved)))
    await user.click(screen.getByRole('button', { name: 'stub-timeout' }))
    expect(await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })).toBeVisible()
  })

  it('shows the finished screen straight away for a player who already played today', async () => {
    await apiClient.POST('/api/v1/daily/today/start')
    await apiClient.POST('/api/v1/daily/today/guesses', { body: { trackId: ANSWER_ID } })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument()
  })

  it('goes back to the start screen when the countdown reaches zero', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/daily/today', () => {
        calls += 1
        if (calls > 1) return HttpResponse.json(notStarted)
        const serverTime = new Date().toISOString()
        return HttpResponse.json({
          ...solved,
          serverTime,
          nextResetAt: new Date(Date.now() + 1200).toISOString(),
          result: { ...solved.result, nextResetAt: new Date(Date.now() + 1200).toISOString() },
        })
      }),
    )
    renderPage()
    await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })
    expect(await screen.findByRole('button', { name: 'Bắt đầu' }, { timeout: 4000 })).toBeVisible()
  })
})

describe('DailyPage: sharing (BR-25)', () => {
  async function finishWithRightGuessAtLevel2(start = 'Bắt đầu', done = 'Bạn đã chơi hôm nay') {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: start }))
    await screen.findByTestId('round')
    await typeAndGuess(user, WRONG_ID)
    await screen.findByText('level 2')
    await typeAndGuess(user, ANSWER_ID)
    await screen.findByRole('heading', { name: done })
    return user
  }

  it('copies the spoiler-free result to the clipboard', async () => {
    const user = await finishWithRightGuessAtLevel2()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')
    const button = screen.getByRole('button', { name: 'Chia sẻ kết quả' })
    await waitFor(() => {
      expect(button).toBeEnabled()
    })
    await user.click(button)

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledTimes(1)
    })
    expect(writeText).toHaveBeenCalledWith(
      `Meloo · 29/10/2026\n🟥🟩⬜⬜⬜ Đúng ở 5s\n${window.location.origin}/daily`,
    )
    expect(writeText.mock.calls[0]?.[0]).not.toContain('Chúng Ta Của Hiện Tại')
    await waitFor(() => {
      expect(useToastStore.getState().toasts.at(-1)?.title).toBe('Đã chép kết quả vào bộ nhớ tạm.')
    })
  })

  it('opens the share sheet on a phone', async () => {
    const user = await finishWithRightGuessAtLevel2()
    const share = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'share', { value: share, configurable: true })
    try {
      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Chia sẻ kết quả' })).toBeEnabled()
      })
      await user.click(screen.getByRole('button', { name: 'Chia sẻ kết quả' }))
      await waitFor(() => {
        expect(share).toHaveBeenCalledTimes(1)
      })
      expect(share.mock.calls[0]?.[0]).toMatchObject({
        text: expect.stringContaining('🟥🟩⬜⬜⬜ Đúng ở 5s') as string,
      })
    } finally {
      Reflect.deleteProperty(navigator, 'share')
    }
  })

  it('builds the note in the language of the interface', async () => {
    await i18n.changeLanguage('en')
    const user = await finishWithRightGuessAtLevel2('Start', 'You played today')
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Share result' })).toBeEnabled()
    })
    await user.click(screen.getByRole('button', { name: 'Share result' }))
    await waitFor(() => {
      expect(writeText).toHaveBeenCalled()
    })
    expect(writeText.mock.calls[0]?.[0]).toContain('🟥🟩⬜⬜⬜ Right at 5s')
  })

  it('tells the player when the copy fails', async () => {
    const user = await finishWithRightGuessAtLevel2()
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Chia sẻ kết quả' })).toBeEnabled()
    })
    await user.click(screen.getByRole('button', { name: 'Chia sẻ kết quả' }))
    await waitFor(() => {
      expect(useToastStore.getState().toasts.at(-1)?.title).toBe(
        'Không chép được kết quả. Hãy thử lại.',
      )
    })
  })
})

describe('DailyPage: whose data it shows', () => {
  it("never shows the previous player's result to a new session in the same tab", async () => {
    const user = userEvent.setup()
    signInAs('player-a')
    const router = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await screen.findByTestId('round')
    await typeAndGuess(user, ANSWER_ID)
    await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })
    expect(screen.getByText('Chúng Ta Của Hiện Tại')).toBeVisible()

    // Log out, and a new guest arrives: the server has nothing for them yet.
    server.use(http.get('*/api/v1/daily/today', () => HttpResponse.json(notStarted)))
    signInAs('player-b')

    expect(await screen.findByRole('button', { name: 'Bắt đầu' })).toBeVisible()
    expect(screen.queryByText('Chúng Ta Của Hiện Tại')).not.toBeInTheDocument()
    const keys = router.client
      .getQueryCache()
      .findAll({ queryKey: ['daily'] })
      .map((query) => query.queryKey[1])
    expect(keys.length).toBeGreaterThan(0)
    expect(keys.every((key) => key === 'player-b')).toBe(true)
  })

  it('asks the server again when the player comes back, so the level and the clock are current', async () => {
    const user = userEvent.setup()
    signInAs('player-a')
    const router = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await screen.findByText('level 1')
    await user.click(screen.getByRole('button', { name: 'stub-listen' }))
    await screen.findByText('level 2')

    await act(async () => {
      await router.navigate('/start')
    })
    await screen.findByText('start page')
    await act(async () => {
      await router.navigate('/daily')
    })

    // Not the snapshot taken when the attempt started (level 1).
    expect(await screen.findByText('level 2')).toBeVisible()
    expect(screen.queryByText('level 1')).not.toBeInTheDocument()
  })

  it('shows the finished screen when the attempt ran out while the player was away', async () => {
    const user = userEvent.setup()
    signInAs('player-a')
    const router = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Bắt đầu' }))
    await screen.findByTestId('round')
    await act(async () => {
      await router.navigate('/start')
    })
    await screen.findByText('start page')
    server.use(http.get('*/api/v1/daily/today', () => HttpResponse.json(solved)))
    await act(async () => {
      await router.navigate('/daily')
    })
    expect(await screen.findByRole('heading', { name: 'Bạn đã chơi hôm nay' })).toBeVisible()
  })
})
