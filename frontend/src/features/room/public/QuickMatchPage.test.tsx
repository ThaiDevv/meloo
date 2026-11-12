import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { useRoomStore } from '@/shared/realtime'
import { installFakeStomp, type FakeStompKit } from '@/shared/realtime/testing/fakeStomp'
import {
  GUEST_ID,
  ROOM_ID,
  SERVER_TIME,
  THIRD_ID,
  player,
  quickMatchRoom,
} from '@/shared/realtime/testing/fixtures'
import { useToastStore } from '@/shared/ui/toast-store'
import { renderPublic, signInAs, signOutAll } from './testing'

const LOBBY = `/topic/rooms/${ROOM_ID}/lobby`
const ROUND = `/topic/rooms/${ROOM_ID}/round`
const problem = { 'Content-Type': 'application/problem+json' }

let kit: FakeStompKit

function answerQuickMatch() {
  const bodies: unknown[] = []
  server.use(
    http.post('*/api/v1/rooms/quick-match', async ({ request }) => {
      bodies.push(await request.json().catch(() => null))
      return HttpResponse.json(quickMatchRoom({ filter: { genreCodes: ['pop'] } }))
    }),
  )
  return bodies
}

async function startSearch() {
  signInAs(GUEST_ID)
  renderPublic('/quick-match')
  await userEvent.click(await screen.findByRole('button', { name: 'Tìm trận' }))
  await screen.findByRole('heading', { name: /Đang tìm người chơi/ })
}

async function connectSocket() {
  await waitFor(() => {
    expect(kit.clients).toHaveLength(1)
  })
  act(() => {
    kit.latest().connect()
  })
}

function lobbyEvent(event: object) {
  act(() => {
    kit.latest().emit(LOBBY, { ...event, serverTime: SERVER_TIME })
  })
}

beforeEach(() => {
  kit = installFakeStomp()
  useRoomStore.getState().reset()
})
afterEach(() => {
  vi.useRealTimers()
  kit.uninstall()
  signOutAll()
  useToastStore.setState({ toasts: [] })
})

describe('entering the queue (AC2)', () => {
  it('sends the chosen music filter and shows the waiting screen with the time waited', async () => {
    const bodies = answerQuickMatch()
    vi.useFakeTimers({ shouldAdvanceTime: true })
    await startSearch()
    expect(bodies).toEqual([{}])
    expect(screen.getByTestId('queue-elapsed')).toHaveTextContent('0:00')
    act(() => {
      vi.advanceTimersByTime(5000)
    })
    expect(screen.getByTestId('queue-elapsed')).toHaveTextContent('0:05')
  })

  it('invites a guest to sign in instead of letting them queue', async () => {
    signInAs(GUEST_ID, 'GUEST')
    renderPublic('/quick-match')
    expect(await screen.findByTestId('quick-guest-invite')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tìm trận' })).not.toBeInTheDocument()
  })

  it('explains when the music has too few tracks', async () => {
    signInAs(GUEST_ID)
    server.use(
      http.post('*/api/v1/rooms/quick-match', () =>
        HttpResponse.json(
          {
            code: 'NOT_ENOUGH_TRACKS',
            status: 422,
            title: 't',
            type: 'about:blank',
            missingTracks: 3,
          },
          { status: 422, headers: problem },
        ),
      ),
    )
    renderPublic('/quick-match')
    await userEvent.click(await screen.findByRole('button', { name: 'Tìm trận' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('thiếu 3 bài')
  })
})

describe('waiting for a second player (AC2)', () => {
  it('shows the 30 s countdown once someone joins, then follows the match', async () => {
    answerQuickMatch()
    await startSearch()
    await waitFor(() => {
      expect(kit.clients).toHaveLength(1)
    })
    act(() => {
      kit.latest().connect()
    })
    lobbyEvent({ type: 'MATCH_STARTING', countdownSeconds: 30 })
    expect(screen.getByRole('timer')).toHaveTextContent('30')
    lobbyEvent({ type: 'MATCH_COUNTDOWN', remainingSeconds: 29 })
    expect(screen.getByRole('timer')).toHaveTextContent('29')

    act(() => {
      kit.latest().emit(ROUND, {
        type: 'ROUND_PREPARE',
        roundNo: 1,
        totalRounds: 5,
        serverTime: SERVER_TIME,
      })
    })
    expect(await screen.findByTestId('where')).toHaveTextContent(`play:/r/${ROOM_ID}/play:state`)
  })

  it('goes back to searching when the countdown stops', async () => {
    answerQuickMatch()
    await startSearch()
    await waitFor(() => {
      expect(kit.clients).toHaveLength(1)
    })
    act(() => {
      kit.latest().connect()
    })
    lobbyEvent({ type: 'MATCH_STARTING', countdownSeconds: 30 })
    lobbyEvent({ type: 'MATCH_COUNTDOWN_STOPPED', reason: 'PLAYER_LEFT' })
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Đang tìm người chơi/ })).toBeInTheDocument()
  })

  it('leaves the queue when cancelled and returns to the music choice', async () => {
    answerQuickMatch()
    await startSearch()
    await waitFor(() => {
      expect(kit.clients).toHaveLength(1)
    })
    act(() => {
      kit.latest().connect()
    })
    await userEvent.click(screen.getByRole('button', { name: 'Hủy tìm trận' }))
    expect(await screen.findByRole('button', { name: 'Tìm trận' })).toBeInTheDocument()
    expect(useRoomStore.getState().room).toBeNull()
  })
})

describe('waiting 60 seconds alone (AC3)', () => {
  async function waitAlone() {
    answerQuickMatch()
    vi.useFakeTimers({ shouldAdvanceTime: true })
    await startSearch()
    await connectSocket()
    expect(screen.queryByText('Chưa có ai vào cùng bạn')).not.toBeInTheDocument()
    act(() => {
      vi.advanceTimersByTime(60_000)
    })
  }

  it('offers solo play and a private room, and lets the player keep waiting', async () => {
    await waitAlone()
    expect(screen.getByText('Chưa có ai vào cùng bạn')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chuyển sang chơi đơn' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tạo phòng riêng và mời bạn/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hủy tìm trận' })).toBeInTheDocument()
    expect(screen.getByTestId('queue-elapsed')).toHaveTextContent('1:00')
  })

  it('switches to solo play', async () => {
    await waitAlone()
    await userEvent.click(screen.getByRole('button', { name: 'Chuyển sang chơi đơn' }))
    expect(await screen.findByTestId('where')).toHaveTextContent('solo:/solo')
  })

  it('leaves the queue, makes a private room with the same filter and copies its link', async () => {
    const created: unknown[] = []
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    server.use(
      http.post('*/api/v1/rooms', async ({ request }) => {
        created.push(await request.json())
        return HttpResponse.json(
          quickMatchRoom({ code: 'PRV234', hostId: GUEST_ID, roomType: 'PRIVATE_ROOM' }),
          { status: 201 },
        )
      }),
    )
    await waitAlone()
    await userEvent.click(screen.getByRole('button', { name: /Tạo phòng riêng và mời bạn/ }))
    expect(await screen.findByTestId('where')).toHaveTextContent('lobby:/r/PRV234:state')
    expect(created).toMatchObject([
      { roomType: 'PRIVATE_ROOM', roundCount: 10, maxPlayers: 5, filter: { genreCodes: ['pop'] } },
    ])
    expect(writeText).toHaveBeenCalledWith(`${location.origin}/r/PRV234`)
  })

  it('shows the hint as soon as the server suggests it, and not when someone joined', async () => {
    answerQuickMatch()
    await startSearch()
    await waitFor(() => {
      expect(kit.clients).toHaveLength(1)
    })
    act(() => {
      kit.latest().connect()
    })
    lobbyEvent({ type: 'MATCH_SOLO_SUGGESTION', message: 'x' })
    expect(screen.getByText('Chưa có ai vào cùng bạn')).toBeInTheDocument()
    lobbyEvent({
      type: 'PLAYER_JOINED',
      user: player({ userId: THIRD_ID, nickname: 'Third' }),
    })
    expect(screen.queryByText('Chưa có ai vào cùng bạn')).not.toBeInTheDocument()
  })
})

describe('leaving the queue for real', () => {
  it('sends the leave command before it creates the private room', async () => {
    answerQuickMatch()
    const order: string[] = []
    server.use(
      http.post('*/api/v1/rooms', () => {
        order.push(`create(after ${String(kit.latest().commands('leave').length)} leave)`)
        return HttpResponse.json(
          quickMatchRoom({ code: 'PRV234', hostId: GUEST_ID, roomType: 'PRIVATE_ROOM' }),
          { status: 201 },
        )
      }),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    await startSearch()
    await connectSocket()
    act(() => {
      vi.advanceTimersByTime(60_000)
    })
    await userEvent.click(screen.getByRole('button', { name: /Tạo phòng riêng và mời bạn/ }))
    await screen.findByTestId('where')
    expect(order).toEqual(['create(after 1 leave)'])
  })

  it('does not follow the match once the player chose to leave', async () => {
    answerQuickMatch()
    server.use(http.post('*/api/v1/rooms', () => new Promise<Response>(() => undefined)))
    vi.useFakeTimers({ shouldAdvanceTime: true })
    await startSearch()
    await connectSocket()
    act(() => {
      vi.advanceTimersByTime(60_000)
    })
    await userEvent.click(screen.getByRole('button', { name: /Tạo phòng riêng và mời bạn/ }))
    lobbyEvent({ type: 'MATCH_STARTING', countdownSeconds: 30 })
    act(() => {
      kit.latest().emit(ROUND, {
        type: 'ROUND_PREPARE',
        roundNo: 1,
        totalRounds: 5,
        serverTime: SERVER_TIME,
      })
    })
    expect(screen.queryByTestId('where')).not.toBeInTheDocument()
  })

  it('waits for the connection before it says the player left', async () => {
    answerQuickMatch()
    await startSearch()
    await connectSocket()
    act(() => {
      kit.latest().drop()
    })
    await userEvent.click(screen.getByRole('button', { name: 'Hủy tìm trận' }))
    expect(kit.latest().commands('leave')).toEqual([])
    expect(screen.getByText(/Đang rời hàng chờ/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tìm trận' })).not.toBeInTheDocument()

    act(() => {
      kit.latest().connect()
    })
    expect(await screen.findByRole('button', { name: 'Tìm trận' })).toBeInTheDocument()
    expect(kit.latest().commands('leave')).toHaveLength(1)
  })
})

describe('a match that started without the live events (BR-14)', () => {
  it('opens the match from a resync snapshot', async () => {
    answerQuickMatch()
    await startSearch()
    await connectSocket()
    act(() => {
      kit.latest().emit('/user/queue/private', {
        type: 'RESYNC_STATE',
        room: quickMatchRoom({ status: 'PLAYING', currentRound: 1 }),
        roomPhase: 'ROUND_IN_PROGRESS',
        currentRound: null,
        scores: [],
        lastRoundAnswer: null,
        lastRoundResults: null,
        serverTime: SERVER_TIME,
      })
    })
    expect(await screen.findByTestId('where')).toHaveTextContent(`play:/r/${ROOM_ID}/play`)
  })
})

describe('a store that still holds the previous match', () => {
  it('stays in the queue of a new room', async () => {
    answerQuickMatch()
    // The finished match A is still in the shared store when the player queues for room B.
    useRoomStore.getState().open('previous-room', quickMatchRoom({ roomId: 'previous-room' }))
    act(() => {
      useRoomStore.getState().receive({
        type: 'MATCH_FINISHED',
        serverTime: SERVER_TIME,
      } as never)
    })
    expect(useRoomStore.getState().phase).toBe('FINISHED')

    await startSearch()
    expect(screen.queryByTestId('where')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hủy tìm trận' })).toBeInTheDocument()
  })
})
