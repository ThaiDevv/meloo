import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { useSnippetPlaying } from '../audio/playbackStore'
import { gameAudioHandlers } from '../mocks/audio'
import { FakeAudioContext } from '../mocks/fakeAudioContext'
import {
  mockResultNoCover,
  mockResultWithCover,
  mockRound,
  mockSuggestions,
} from '../mocks/fixtures'
import { GameRound, type GameRoundProps } from './GameRound'

function VoiceProbe() {
  return <p data-testid="voice-muted">{String(useSnippetPlaying())}</p>
}

function renderRound(props: Partial<GameRoundProps> = {}) {
  const handlers = {
    onSearch: vi.fn(),
    onGuess: vi.fn(),
    onListenMore: vi.fn(),
    onSkip: vi.fn(),
    onTimeout: vi.fn(),
    onContinue: vi.fn(),
  }
  const base: GameRoundProps = {
    round: mockRound(3),
    totalScore: 1600,
    suggestions: mockSuggestions,
    ...handlers,
    ...props,
  }
  const view = render(
    <>
      <GameRound {...base} />
      <VoiceProbe />
    </>,
  )
  const rerender = (next: Partial<GameRoundProps>) => {
    view.rerender(
      <>
        <GameRound {...base} {...next} />
        <VoiceProbe />
      </>,
    )
  }
  return { ...handlers, rerender }
}

beforeEach(() => {
  FakeAudioContext.reset()
  vi.stubGlobal('AudioContext', FakeAudioContext)
  server.use(...gameAudioHandlers)
})
afterEach(() => {
  vi.unstubAllGlobals()
  FakeAudioContext.reset()
})

describe('GameRound: levels (AC 2)', () => {
  it('shows the open level and the points still on offer', async () => {
    renderRound({ round: mockRound(3) })
    expect(screen.getByText('Vòng 3 / 10')).toBeInTheDocument()
    expect(screen.getByTestId('points-left')).toHaveTextContent('600')
    const current = screen.getByRole('listitem', { name: /đang mở/ })
    expect(current).toHaveAccessibleName('Mức 3: 10s, 600 điểm, đang mở')
    expect(screen.getAllByRole('listitem', { name: /đã mở/ })).toHaveLength(2)
    expect(screen.getAllByRole('listitem', { name: /chưa mở/ })).toHaveLength(2)
    await waitFor(() => {
      expect(screen.getByText('Đang phát 10s')).toBeInTheDocument()
    })
  })

  it('moves up a level: points drop by 200 and the new clip plays', async () => {
    const { rerender } = renderRound({ round: mockRound(2) })
    await waitFor(() => {
      expect(screen.getByText('Đang phát 5s')).toBeInTheDocument()
    })
    expect(screen.getByTestId('points-left')).toHaveTextContent('800')

    rerender({ round: mockRound(3) })
    expect(screen.getByTestId('points-left')).toHaveTextContent('600')
    await waitFor(() => {
      expect(screen.getByText('Đang phát 10s')).toBeInTheDocument()
    })
    // One AudioContext for the whole screen: sound stays unlocked across levels.
    expect(FakeAudioContext.instances).toHaveLength(1)
    expect(FakeAudioContext.instances[0]?.sources).toHaveLength(2)
    expect(FakeAudioContext.instances[0]?.sources[1]?.startArgs).toEqual([0, 0, 10])
  })

  it('asks to open the next level with the level it expects (guards double clicks)', async () => {
    const { onListenMore } = renderRound({ round: mockRound(3) })
    await userEvent.click(screen.getByRole('button', { name: /Nghe thêm/ }))
    expect(onListenMore).toHaveBeenCalledExactlyOnceWith(4)
  })

  it('swaps "Nghe thêm" for "Bỏ qua" at 30 seconds (BR-04)', async () => {
    const { onSkip } = renderRound({ round: mockRound(5) })
    expect(screen.queryByRole('button', { name: /Nghe thêm/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Bỏ qua/ }))
    expect(onSkip).toHaveBeenCalledOnce()
  })

  it('gives up from the keyboard at 30 seconds', async () => {
    const { onSkip } = renderRound({ round: mockRound(5) })
    screen.getByRole('button', { name: /Bỏ qua/ }).focus()
    await userEvent.keyboard('{Enter}')
    expect(onSkip).toHaveBeenCalledOnce()
  })

  it('decodes the next round ahead of time (AC 6)', async () => {
    const urls: string[] = []
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.get('*/api/v1/audio/:token', ({ params }) => {
        urls.push(String(params.token))
        return new HttpResponse(new ArrayBuffer(8))
      }),
    )
    renderRound({ round: mockRound(1), preloadUrls: [mockRound(1, 4).audio.url] })
    await waitFor(() => {
      expect(urls).toHaveLength(2)
    })
    expect(urls).toContain('mock-level-1-round-4-Hq3TnV8wYb5RsLd2')
  })

  it('does not let a second click through while a request is in flight', () => {
    renderRound({ round: mockRound(3), busy: true })
    expect(screen.getByRole('button', { name: /Nghe thêm/ })).toBeDisabled()
  })

  it('works from the keyboard: Nghe thêm and Nghe lại (NFR-13)', async () => {
    const { onListenMore } = renderRound({ round: mockRound(2) })
    await waitFor(() => {
      expect(screen.getByText('Đang phát 5s')).toBeInTheDocument()
    })
    const user = userEvent.setup()

    screen.getByRole('button', { name: /Nghe thêm/ }).focus()
    await user.keyboard('{Enter}')
    expect(onListenMore).toHaveBeenCalledWith(3)

    screen.getByRole('button', { name: /Nghe lại/ }).focus()
    await user.keyboard(' ')
    await waitFor(() => {
      expect(FakeAudioContext.instances[0]?.sources).toHaveLength(2)
    })
  })

  it('shows the combo streak and what the next right guess is worth (BR-08)', () => {
    const { rerender } = renderRound({ combo: { streak: 0 } })
    expect(screen.getByText('Chưa có chuỗi')).toBeInTheDocument()

    rerender({ combo: { streak: 1 } })
    expect(screen.getByText('Chuỗi 1 vòng')).toBeInTheDocument()
    expect(screen.getByText(/vòng sau \+10%/)).toBeInTheDocument()

    rerender({ combo: { streak: 3 } })
    expect(screen.getByText(/vòng sau \+20%/)).toBeInTheDocument()
  })

  it('leaves the combo out where the mode has none (daily challenge)', () => {
    renderRound({ combo: undefined })
    expect(screen.queryByText(/Chuỗi|chuỗi/)).not.toBeInTheDocument()
  })
})

describe('GameRound: guessing (AC 2, 4, 5)', () => {
  it('guesses the chosen track', async () => {
    const { onGuess } = renderRound()
    const user = userEvent.setup()
    await user.type(screen.getByRole('combobox'), 'chung')
    await user.click(await screen.findByRole('option', { name: /Hiện Tại/ }))
    await user.click(screen.getByRole('button', { name: 'Đoán' }))
    expect(onGuess).toHaveBeenCalledExactlyOnceWith(mockSuggestions[0]?.id)
  })

  it('says "Đúng ca sĩ!" for the right artist and still counts a wrong guess (BR-06)', () => {
    renderRound({ verdict: { correct: false, rightArtist: true, nonce: 1 } })
    expect(screen.getByText('Đúng ca sĩ!')).toBeInTheDocument()
    expect(screen.getByText('Nhưng chưa đúng bài.')).toBeInTheDocument()
  })

  it('names the level that opened after a wrong guess', () => {
    renderRound({ round: mockRound(4), verdict: { correct: false, rightArtist: false, nonce: 2 } })
    expect(screen.getByText('Chưa đúng. Đã mở mức 20s.')).toBeInTheDocument()
  })

  it('confirms a right guess', () => {
    renderRound({ verdict: { correct: true, rightArtist: false, nonce: 3 } })
    expect(screen.getByText('Chính xác!')).toBeInTheDocument()
  })

  it('shows a round clock only when the round has a time limit and tells the host when it runs out', async () => {
    const view = renderRound()
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()

    view.rerender({ timer: { serverTime: '2026-10-23T13:10:00Z', endsAt: '2026-10-23T13:10:01Z' } })
    expect(screen.getByRole('timer')).toBeInTheDocument()
    await waitFor(
      () => {
        expect(view.onTimeout).toHaveBeenCalledOnce()
      },
      { timeout: 3000 },
    )
  })
})

describe('GameRound: sound (AC 6, 8)', () => {
  it('tells voice chat while the clip plays and when it stops (BR-19)', async () => {
    renderRound()
    await waitFor(() => {
      expect(screen.getByTestId('voice-muted')).toHaveTextContent('true')
    })
    FakeAudioContext.instances[0]?.sources[0]?.finish()
    await waitFor(() => {
      expect(screen.getByTestId('voice-muted')).toHaveTextContent('false')
    })
  })

  it('asks for a tap when the browser blocks autoplay, then plays', async () => {
    FakeAudioContext.allowSound = false
    renderRound()
    const tap = await screen.findByRole('button', { name: 'Chạm để nghe' })
    FakeAudioContext.allowSound = true
    await userEvent.click(tap)
    await waitFor(() => {
      expect(screen.getByText('Đang phát 10s')).toBeInTheDocument()
    })
    expect(screen.queryByRole('button', { name: 'Chạm để nghe' })).not.toBeInTheDocument()
  })

  it('shows a load error with a retry instead of hanging', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(http.get('*/api/v1/audio/:token', () => new HttpResponse(null, { status: 500 })))
    renderRound()
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Không tải được đoạn nhạc')).toBeInTheDocument()
    expect(within(alert).getByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
  })

  it('asks the host for a fresh link when the clip link expired', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(http.get('*/api/v1/audio/:token', () => new HttpResponse(null, { status: 401 })))
    const onAudioExpired = vi.fn()
    renderRound({ onAudioExpired })
    await waitFor(() => {
      expect(onAudioExpired).toHaveBeenCalled()
    })
  })
})

describe('GameRound: the answer (AC 5, 7)', () => {
  it('never puts the answer on screen while the round is open', async () => {
    renderRound({ round: mockRound(5) })
    await waitFor(() => {
      expect(screen.getByText('Đang phát 30s')).toBeInTheDocument()
    })
    expect(document.body).not.toHaveTextContent('Chúng Ta Của Hiện Tại')
    expect(document.body).not.toHaveTextContent('Sơn Tùng')
    expect(screen.queryByRole('link', { name: /Deezer/ })).not.toBeInTheDocument()
  })

  it('swaps to the reveal once the server publishes the result, and stops the music', async () => {
    const { onContinue, rerender } = renderRound({ round: mockRound(2) })
    await waitFor(() => {
      expect(screen.getByTestId('voice-muted')).toHaveTextContent('true')
    })

    rerender({ result: mockResultWithCover })
    expect(
      screen.getByRole('heading', { level: 2, name: /Chúng Ta Của Hiện Tại/ }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByTestId('voice-muted')).toHaveTextContent('false')
    })

    await userEvent.click(screen.getByRole('button', { name: 'Vòng tiếp theo' }))
    expect(onContinue).toHaveBeenCalledOnce()
  })

  it('shows the answer screen without a cover', () => {
    renderRound({ result: mockResultNoCover })
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Chúng Ta Của Hiện Tại')
    expect(screen.getByRole('img', { name: 'Chưa có ảnh bìa' })).toBeInTheDocument()
  })
})
