import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { useAuthStore } from '@/shared/api/authStore'
import { useRoomStore } from '@/shared/realtime'
import type { RoomResponse } from '@/shared/realtime/messages'
import { installFakeStomp, type FakeStompKit } from '@/shared/realtime/testing/fakeStomp'
import {
  GUEST_ID,
  HOST_ID,
  ROOM_ID,
  THIRD_ID,
  host,
  player,
  quickMatchRoom,
  room,
} from '@/shared/realtime/testing/fixtures'
import { FakeAudioContext } from '../../game-core/mocks/fakeAudioContext'
import { SnippetPlayer } from '../../game-core/audio/snippetPlayer'
import { MatchPage } from './MatchPage'

vi.mock('@/features/chat', () => ({
  RoomChat: ({ roomId }: { roomId: string }) => <div data-testid="room-chat">{roomId}</div>,
}))

const T = '2026-11-05T10:00:00Z'
const LOBBY = `/topic/rooms/${ROOM_ID}/lobby`
const ROUND = `/topic/rooms/${ROOM_ID}/round`
const BOARD = `/topic/rooms/${ROOM_ID}/scoreboard`
const PRIVATE = '/user/queue/private'

const ANSWER = {
  trackId: '33333333-4444-5555-6666-777777777777',
  trackTitle: 'Cắt Đôi Nỗi Sầu',
  artistName: 'Tăng Duy Tân',
  albumTitle: null,
  coverUrl: null,
  previewFullUrl: 'https://www.deezer.com/track/12345678',
}

let kit: FakeStompKit

function playing(base: RoomResponse = room()): RoomResponse {
  return { ...base, status: 'PLAYING', currentRound: 2 }
}

function renderMatch(initial: RoomResponse = playing(), seeded = true) {
  useAuthStore.setState({
    accessToken: 'test-token',
    user: {
      id: GUEST_ID,
      kind: 'REGISTERED',
      role: 'USER',
      nickname: 'PlayerTwo',
      createdAt: '2026-10-01T00:00:00Z',
    },
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const code = initial.code ?? initial.roomId
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>
        <MemoryRouter
          initialEntries={[
            { pathname: `/r/${code}/play`, state: seeded ? { room: initial } : undefined },
          ]}
        >
          <Routes>
            <Route path="/r/:code/play" element={<MatchPage />} />
            <Route path="/r/:code" element={<p>trang sảnh</p>} />
            <Route path="/quick-match" element={<p>trang ghép trận</p>} />
            <Route path="/" element={<p>trang chủ</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

function snapshot(r: RoomResponse, phase: string, extra: object = {}) {
  return {
    type: 'RESYNC_STATE',
    room: r,
    roomPhase: phase,
    currentRound: {
      roundNo: 2,
      totalRounds: 10,
      currentLevel: 2,
      audioClip: { url: '/api/v1/audio/r2', level: 2, expiresAt: '2026-11-05T10:05:00Z' },
      remainingMs: 35000,
      hasGuessed: false,
    },
    scores: [
      { userId: HOST_ID, nickname: 'ThaiPO', totalScore: 1013, comboStreak: 2, rank: 1 },
      { userId: GUEST_ID, nickname: 'PlayerTwo', totalScore: 400, comboStreak: 0, rank: 2 },
    ],
    lastRoundAnswer: null,
    lastRoundResults: null,
    serverTime: T,
    ...extra,
  }
}

/** The socket connects; the server answers the resync with a round in progress. */
async function goLive(r: RoomResponse = playing(), phase = 'ROUND_IN_PROGRESS', extra = {}) {
  await waitFor(() => {
    expect(kit.clients).toHaveLength(1)
  })
  act(() => {
    kit.latest().connect()
    kit.latest().emit(PRIVATE, snapshot(r, phase, extra))
  })
}

function emit(destination: string, message: object) {
  act(() => {
    kit.latest().emit(destination, { ...message, serverTime: T })
  })
}

const guessedByHost = {
  type: 'PLAYER_GUESSED',
  userId: HOST_ID,
  nickname: 'ThaiPO',
  level: 5,
  isCorrect: true,
  roundScore: 1013,
  totalScore: 2026,
}

beforeEach(() => {
  FakeAudioContext.reset()
  vi.stubGlobal('AudioContext', FakeAudioContext)
  kit = installFakeStomp()
  useRoomStore.getState().reset()
})
afterEach(() => {
  kit.uninstall()
  useAuthStore.setState({ accessToken: null, user: null })
  vi.useRealTimers()
  vi.unstubAllGlobals()
  FakeAudioContext.reset()
})

describe('the round in progress', () => {
  it('shows the round from the snapshot and mounts the chat', async () => {
    renderMatch()
    await goLive()
    expect(await screen.findByText('Vòng 2 / 10')).toBeInTheDocument()
    expect(screen.getByRole('timer')).toBeInTheDocument()
    expect(screen.getByTestId('room-chat')).toHaveTextContent(ROOM_ID)
  })

  it('asks for the whole picture when it arrives from the lobby with a live link', async () => {
    useRoomStore.getState().open(ROOM_ID, playing())
    renderMatch()
    await waitFor(() => {
      expect(kit.clients).toHaveLength(1)
    })
    act(() => {
      kit.latest().connect()
    })
    // The shared connection asks once on connect; the snapshot makes the link live.
    expect(kit.latest().commands('resync')).toHaveLength(1)
  })

  it('never shows the answer while the round runs', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(ROUND, guessedByHost)
    expect(document.body).not.toHaveTextContent('Cắt Đôi Nỗi Sầu')
  })

  it('sends a guess for the open round and the give-up for the last level only', async () => {
    renderMatch()
    await goLive()
    const user = userEvent.setup()
    await screen.findByText('Vòng 2 / 10')
    await user.click(screen.getByRole('button', { name: /nghe thêm/i }))
    expect(kit.latest().commands('listen-more')).toEqual([{ roundNo: 2, currentLevel: 2 }])
    expect(screen.queryByRole('button', { name: /bỏ qua/i })).not.toBeInTheDocument()
  })

  it('swaps the clip when the server opens the next level', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(PRIVATE, {
      type: 'LISTEN_MORE_RESPONSE',
      roundNo: 2,
      level: 5,
      audioClip: { url: '/api/v1/audio/r2l5', level: 5, expiresAt: '2026-11-05T10:05:00Z' },
    })
    await waitFor(() => {
      expect(screen.getByTestId('points-left')).toHaveTextContent('800')
    })
  })
})

describe('the live table', () => {
  it('lists every player with score and provisional place, the leader first', async () => {
    renderMatch()
    await goLive()
    const board = await screen.findByRole('region', { name: 'Bảng điểm' })
    const cards = within(board).getAllByRole('listitem')
    expect(cards.map((el) => el.getAttribute('aria-label'))).toEqual(['ThaiPO', 'PlayerTwo'])
    expect(within(cards[0] as HTMLElement).getByText('1.013')).toBeInTheDocument()
    expect(within(cards[1] as HTMLElement).getByText('400')).toBeInTheDocument()
  })

  it('announces a right guess with its level and adds the points at once', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(ROUND, guessedByHost)
    expect(await screen.findByText('ThaiPO đúng ở 5s')).toBeInTheDocument()
    const card = screen.getByRole('listitem', { name: 'ThaiPO' })
    expect(within(card).getByText('2.026')).toBeInTheDocument()
    expect(within(card).getByText('+1.013')).toBeInTheDocument()
    expect(within(card).getByText('đúng ở 5s')).toBeInTheDocument()
  })

  it('shows "hết lượt" for a player who gave up', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(ROUND, { type: 'PLAYER_GAVE_UP', userId: HOST_ID, roundNo: 2 })
    expect(
      within(screen.getByRole('listitem', { name: 'ThaiPO' })).getByText('hết lượt'),
    ).toBeInTheDocument()
  })

  it('dims a player who lost the connection and marks one who left', async () => {
    const three = playing(
      room({ players: [host(), player(), player({ userId: THIRD_ID, nickname: 'Chị Ba' })] }),
    )
    renderMatch(three)
    await goLive(three)
    await screen.findByText('Vòng 2 / 10')
    emit(LOBBY, { type: 'PLAYER_CONNECTION_CHANGED', userId: HOST_ID, isConnected: false })
    expect(
      within(screen.getByRole('listitem', { name: 'ThaiPO' })).getByText('Mất kết nối'),
    ).toBeInTheDocument()
    emit(LOBBY, { type: 'PLAYER_LEFT', userId: THIRD_ID, newHostId: null, reason: 'VOLUNTARY' })
    expect(
      within(screen.getByRole('listitem', { name: 'Chị Ba' })).getByText('Đã rời'),
    ).toBeInTheDocument()
  })

  it('shortens a card on a phone and shows the name when it is tapped', async () => {
    renderMatch()
    await goLive()
    const user = userEvent.setup()
    const toggle = await screen.findByRole('button', { name: 'Xem tên ThaiPO' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
  })
})

describe('after this player is done', () => {
  it('shows the score detail of a right guess and waits for the others', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(PRIVATE, {
      type: 'GUESS_RESULT_PRIVATE',
      roundNo: 2,
      result: 'CORRECT',
      isCorrect: true,
      isCorrectArtist: true,
      scoreDetail: { baseScore: 800, comboPercent: 10, speedBonus: 133, roundScore: 1013 },
      nextLevel: null,
      nextAudioClip: null,
    })
    expect(await screen.findByText('Bạn đúng ở 2s!')).toBeInTheDocument()
    expect(screen.getByText('Đang chờ những người còn lại…')).toBeInTheDocument()
    expect(screen.getAllByText('1.013').length).toBeGreaterThan(0)
  })
})

describe('the answer of a round', () => {
  const roundEnd = {
    type: 'ROUND_END',
    roundNo: 2,
    answer: ANSWER,
    results: [
      {
        userId: HOST_ID,
        outcome: 'CORRECT',
        correctLevel: 5,
        baseScore: 800,
        comboPercent: 10,
        speedBonus: 133,
        roundScore: 1013,
        wrongCount: 0,
        answerTimeMs: 15000,
      },
      {
        userId: GUEST_ID,
        outcome: 'WRONG_OUT',
        correctLevel: null,
        baseScore: 0,
        comboPercent: 0,
        speedBonus: 0,
        roundScore: 0,
        wrongCount: 5,
        answerTimeMs: null,
      },
    ],
    nextRoundAt: '2026-11-05T10:00:08Z',
  }

  it('publishes the answer and the points of every player, with the pause before the next round', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(ROUND, roundEnd)
    expect(await screen.findByRole('heading', { name: /Cắt Đôi Nỗi Sầu/ })).toBeInTheDocument()
    const table = screen.getByRole('table')
    const host_ = within(table).getByRole('row', { name: /ThaiPO/ })
    expect(within(host_).getByText('800')).toBeInTheDocument()
    expect(within(host_).getByText('+10%')).toBeInTheDocument()
    expect(within(host_).getByText('+133')).toBeInTheDocument()
    expect(within(host_).getByText('1.013')).toBeInTheDocument()
    expect(screen.getByRole('timer', { name: '' })).toHaveTextContent('Vòng tiếp theo sau 8 giây')
  })

  it('tells the player the round was swapped for a new track, and that the match ended early', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(ROUND, { type: 'ROUND_TRACK_REPLACED', roundNo: 2, reason: 'AUDIO_UNAVAILABLE' })
    expect(await screen.findByText(/Vòng 2 đã đổi sang bài khác/)).toBeInTheDocument()
    emit(ROUND, {
      type: 'MATCH_EARLY_ENDED',
      reason: 'ONLY_ONE_PLAYER_REMAINED',
      remainingUserId: GUEST_ID,
    })
    expect(
      await screen.findByText('Trận kết thúc sớm vì chỉ còn 1 người chơi.'),
    ).toBeInTheDocument()
  })
})

const finished = {
  type: 'MATCH_FINISHED',
  matchId: '55555555-6666-7777-8888-999999999999',
  rankings: [
    {
      rank: 1,
      userId: HOST_ID,
      nickname: 'ThaiPO',
      totalScore: 8450,
      correctCount: 9,
      totalCorrectTimeMs: 1,
      left: false,
    },
    {
      rank: 2,
      userId: GUEST_ID,
      nickname: 'PlayerTwo',
      totalScore: 3200,
      correctCount: 4,
      totalCorrectTimeMs: 2,
      left: false,
    },
    {
      rank: 3,
      userId: THIRD_ID,
      nickname: 'Chị Ba',
      totalScore: 100,
      correctCount: 1,
      totalCorrectTimeMs: 3,
      left: true,
    },
  ],
}

describe('the podium', () => {
  it('shows the top three and "Chơi lại" takes the room back to its lobby (BR-12)', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(BOARD, finished)
    expect(await screen.findByRole('heading', { name: 'Bục vinh danh' })).toBeInTheDocument()
    const podium = screen.getByRole('region', { name: 'Bục vinh danh' })
    expect(within(podium).getByText('8.450')).toBeInTheDocument()
    expect(within(podium).getByText('Đã rời')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Chơi lại' }))
    expect(await screen.findByText('trang sảnh')).toBeInTheDocument()
  })

  it('follows the room when the server sends everyone back to the lobby', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    emit(BOARD, finished)
    emit(LOBBY, { type: 'RETURN_TO_LOBBY' })
    expect(await screen.findByText('trang sảnh')).toBeInTheDocument()
  })

  it('rebuilds the table from the scores when the match ended before this screen loaded', async () => {
    renderMatch()
    await goLive(playing(), 'FINISHED', { currentRound: null })
    const podium = await screen.findByRole('region', { name: 'Bục vinh danh' })
    expect(within(podium).getByText('1.013')).toBeInTheDocument()
  })
})

describe('rank points in a quick match (BR-13, BR-24)', () => {
  const quick = () =>
    playing(
      quickMatchRoom({
        players: [host({ isHost: false }), player()],
        roomId: ROOM_ID,
      }),
    )

  it('waits for RANK_UPDATED, then shows the change and the new rank points', async () => {
    renderMatch(quick())
    await goLive(quick())
    await screen.findByText('Vòng 2 / 10')
    emit(BOARD, finished)
    expect((await screen.findAllByText('Đang tính điểm rank…')).length).toBeGreaterThan(0)
    emit(BOARD, {
      type: 'RANK_UPDATED',
      matchId: finished.matchId,
      changes: [
        { userId: HOST_ID, rankDelta: 30, rankPoints: 180 },
        { userId: GUEST_ID, rankDelta: -5, rankPoints: 95 },
      ],
    })
    expect(await screen.findByText('+30')).toBeInTheDocument()
    expect(screen.getByText('−5')).toBeInTheDocument()
    expect(screen.getByText('180 điểm rank')).toBeInTheDocument()
    expect(screen.queryByText('Đang tính điểm rank…')).not.toBeInTheDocument()
  })

  it('gives up after 5 seconds and points to the profile', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderMatch(quick())
    await goLive(quick())
    await screen.findByText('Vòng 2 / 10')
    emit(BOARD, finished)
    expect((await screen.findAllByText('Đang tính điểm rank…')).length).toBeGreaterThan(0)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_100)
    })
    expect(screen.queryByText('Đang tính điểm rank…')).not.toBeInTheDocument()
    expect(screen.getByText('Xem điểm rank ở hồ sơ')).toBeInTheDocument()
  })

  it('offers no "Chơi lại" where there is no room code', async () => {
    renderMatch(quick())
    await goLive(quick())
    await screen.findByText('Vòng 2 / 10')
    emit(BOARD, finished)
    await screen.findByRole('heading', { name: 'Bục vinh danh' })
    expect(screen.queryByRole('button', { name: 'Chơi lại' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tìm trận mới' })).toBeInTheDocument()
  })
})

describe('losing the connection', () => {
  it('shows a banner, then restores the round from the snapshot after reconnecting', async () => {
    renderMatch()
    await goLive()
    await screen.findByText('Vòng 2 / 10')
    act(() => {
      kit.latest().drop()
    })
    expect(await screen.findByText(/Mất kết nối, đang kết nối lại/)).toBeInTheDocument()
    act(() => {
      kit.latest().connect()
    })
    await waitFor(() => {
      expect(kit.latest().commands('resync').length).toBeGreaterThan(0)
    })
    act(() => {
      kit.latest().emit(
        PRIVATE,
        snapshot(playing(), 'ROUND_REVEAL', {
          currentRound: null,
          lastRoundAnswer: ANSWER,
          lastRoundResults: [],
        }),
      )
    })
    await waitFor(() => {
      expect(screen.queryByText(/Mất kết nối, đang kết nối lại/)).not.toBeInTheDocument()
    })
    expect(await screen.findByRole('heading', { name: /Cắt Đôi Nỗi Sầu/ })).toBeInTheDocument()
  })
})

describe('regressions of review round 1', () => {
  it('frees "Nghe thêm" after a reconnect that swallowed the reply (M1)', async () => {
    renderMatch()
    await goLive()
    const user = userEvent.setup()
    await screen.findByText('Vòng 2 / 10')
    await user.click(screen.getByRole('button', { name: /nghe thêm/i }))
    expect(screen.getByRole('button', { name: /nghe thêm/i })).toBeDisabled()
    act(() => {
      kit.latest().drop()
    })
    act(() => {
      kit.latest().connect()
    })
    act(() => {
      kit.latest().emit(PRIVATE, snapshot(playing(), 'ROUND_IN_PROGRESS'))
    })
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /nghe thêm/i })).toBeEnabled()
    })
  })

  it('reopens a quick match from its room id after a reload (M2)', async () => {
    const quick = playing(quickMatchRoom({ players: [host({ isHost: false }), player()] }))
    renderMatch(quick, false)
    expect(screen.queryByText('Mã phòng không hợp lệ')).not.toBeInTheDocument()
    await goLive(quick)
    // The match screen mounts once the room is known and asks for its own snapshot.
    await waitFor(() => {
      expect(kit.latest().commands('resync').length).toBeGreaterThan(1)
    })
    act(() => {
      kit.latest().emit(PRIVATE, snapshot(quick, 'ROUND_IN_PROGRESS'))
    })
    expect(await screen.findByText('Vòng 2 / 10')).toBeInTheDocument()
  })

  it('confirms "prepared" only once the clip is loaded, and again for a new prepare (M4)', async () => {
    let finish: () => void = () => undefined
    const load = vi.spyOn(SnippetPlayer.prototype, 'load').mockImplementation(
      () =>
        new Promise<AudioBuffer>((resolve) => {
          finish = () => {
            resolve({} as AudioBuffer)
          }
        }),
    )
    renderMatch()
    await goLive(playing(), 'ROUND_PREPARE')
    await waitFor(() => {
      expect(load).toHaveBeenCalledWith('/api/v1/audio/r2')
    })
    expect(kit.latest().commands('prepared')).toHaveLength(0)
    await act(async () => {
      finish()
      await Promise.resolve()
    })
    await waitFor(() => {
      expect(kit.latest().commands('prepared')).toEqual([{ roundNo: 2 }])
    })
    // The same round is prepared again after a track swap (BR-27): confirm again.
    emit(ROUND, { type: 'ROUND_PREPARE', roundNo: 2, totalRounds: 10, timeoutMs: 5000 })
    await act(async () => {
      finish()
      await Promise.resolve()
    })
    await waitFor(() => {
      expect(kit.latest().commands('prepared')).toHaveLength(2)
    })
    load.mockRestore()
  })
})
