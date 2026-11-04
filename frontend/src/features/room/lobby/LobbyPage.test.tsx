import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { useRoomStore } from '@/shared/realtime'
import { installFakeStomp, type FakeStompKit } from '@/shared/realtime/testing/fakeStomp'
import {
  GUEST_ID,
  HOST_ID,
  ROOM_ID,
  SERVER_TIME,
  THIRD_ID,
  host,
  player,
  quickMatchRoom,
  room,
} from '@/shared/realtime/testing/fixtures'
import type { RoomResponse } from '@/shared/realtime/messages'
import { useToastStore } from '@/shared/ui/toast-store'
import { renderLobby, signIn, signOut } from './testing/renderLobby'

vi.mock('@/features/chat', () => ({
  RoomChat: ({ roomId }: { roomId: string }) => <div data-testid="room-chat">{roomId}</div>,
}))

const problem = { 'Content-Type': 'application/problem+json' }
const LOBBY = `/topic/rooms/${ROOM_ID}/lobby`
const ROUND = `/topic/rooms/${ROOM_ID}/round`

let kit: FakeStompKit

function answerJoin(respond: () => Response) {
  const bodies: unknown[] = []
  server.use(
    http.post('/api/v1/rooms/join', async ({ request }) => {
      bodies.push(await request.json())
      return respond()
    }),
  )
  return bodies
}

const joinOk =
  (r: RoomResponse = room()) =>
  () =>
    HttpResponse.json(r)

function failWith(status: number, code: string) {
  return () =>
    HttpResponse.json(
      { code, status, title: 't', type: 'about:blank' },
      { status, headers: problem },
    )
}

function snapshot(r: RoomResponse, phase = 'LOBBY') {
  return {
    type: 'RESYNC_STATE',
    room: r,
    roomPhase: phase,
    currentRound: null,
    scores: [],
    lastRoundAnswer: null,
    lastRoundResults: null,
    serverTime: SERVER_TIME,
  }
}

/** The socket connects and the server answers the resync: the lobby is live. */
async function goLive(r: RoomResponse = room()) {
  await waitFor(() => {
    expect(kit.clients).toHaveLength(1)
  })
  act(() => {
    kit.latest().connect()
    kit.latest().emit('/user/queue/private', snapshot(r))
  })
}

function lobbyEvent(event: object) {
  act(() => {
    kit.latest().emit(LOBBY, { ...event, serverTime: SERVER_TIME })
  })
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
  kit = installFakeStomp()
  useRoomStore.getState().reset()
  signIn(GUEST_ID, 'PlayerTwo')
})
afterEach(() => {
  kit.uninstall()
  signOut()
  useToastStore.setState({ toasts: [] })
})

describe('joining by code or link (AC2)', () => {
  it('joins the room whose code is in the link and shows its players', async () => {
    const bodies = answerJoin(joinOk())
    renderLobby('/r/MEL889')

    expect(await screen.findByRole('heading', { level: 1, name: 'Sảnh chờ' })).toBeInTheDocument()
    expect(bodies).toEqual([{ code: 'MEL889' }])
    const list = screen.getByRole('list')
    expect(within(list).getByText('ThaiPO')).toBeInTheDocument()
    expect(within(list).getByText('PlayerTwo')).toBeInTheDocument()
    expect(within(list).getByText('Chủ phòng')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Người chơi (2/5)' })).toBeInTheDocument()
  })

  it('reads a lower-case link as the same code', async () => {
    const bodies = answerJoin(joinOk())
    renderLobby('/r/mel889')
    await screen.findByRole('heading', { level: 1, name: 'Sảnh chờ' })
    expect(bodies).toEqual([{ code: 'MEL889' }])
  })

  it('shows a skeleton while joining', () => {
    answerJoin(() => new Response(null, { status: 200 }))
    renderLobby('/r/MEL889')
    expect(screen.getByRole('status', { name: 'Đang vào phòng…' })).toHaveAttribute(
      'aria-busy',
      'true',
    )
  })

  it.each([
    ['the room is full', 409, 'ROOM_FULL', 'Phòng đã đầy'],
    ['the host removed the player', 403, 'ROOM_KICKED', 'Bạn đã bị mời ra khỏi phòng'],
    ['the room does not exist', 404, 'ROOM_NOT_FOUND', 'Không tìm thấy phòng'],
    ['the account is locked', 403, 'ACCOUNT_LOCKED', 'Tài khoản đang bị khóa'],
  ])('says so plainly when %s', async (_name, status, code, title) => {
    answerJoin(failWith(status, code))
    renderLobby('/r/MEL889')

    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tạo phòng mới' })).toHaveAttribute(
      'href',
      '/rooms/new',
    )
    expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument()
    expect(kit.clients).toHaveLength(0)
  })

  it('does not even ask the server about a code that cannot be one', async () => {
    const bodies = answerJoin(joinOk())
    renderLobby('/r/ABC')
    expect(
      await screen.findByRole('heading', { name: 'Mã phòng không hợp lệ' }),
    ).toBeInTheDocument()
    expect(bodies).toEqual([])
  })

  it('sends someone who is not signed in to /start', async () => {
    answerJoin(failWith(401, 'UNAUTHORIZED'))
    renderLobby('/r/MEL889')
    expect(await screen.findByText('trang bắt đầu')).toBeInTheDocument()
  })

  it('lets the player try again after a failure that may pass', async () => {
    const user = userEvent.setup()
    let attempts = 0
    answerJoin(() => {
      attempts += 1
      return attempts === 1 ? failWith(500, 'INTERNAL_ERROR')() : HttpResponse.json(room())
    })
    renderLobby('/r/MEL889')

    await user.click(await screen.findByRole('button', { name: 'Thử lại' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Sảnh chờ' })).toBeInTheDocument()
    expect(attempts).toBe(2)
  })

  it('tells a player who arrives during a match that they play from the next one (BR-15)', async () => {
    answerJoin(joinOk(room({ status: 'PLAYING', currentRound: 3 })))
    renderLobby('/r/MEL889')

    expect(
      await screen.findByText('Trận đang diễn ra, bạn sẽ chơi từ trận sau'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sẵn sàng' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Rời phòng' })).toBeEnabled()
  })

  it('does not join again when the room comes with the navigation (the creator is inside)', async () => {
    const bodies = answerJoin(joinOk())
    renderLobby('/r/MEL889', { room: room() })

    expect(await screen.findByRole('heading', { level: 1, name: 'Sảnh chờ' })).toBeInTheDocument()
    expect(bodies).toEqual([])
  })

  it('ignores a room that came with the navigation but is not the one in the link', async () => {
    const bodies = answerJoin(joinOk(room({ code: 'ABCDEF', roomId: 'other' })))
    renderLobby('/r/ABCDEF', { room: room() })
    await screen.findByRole('heading', { level: 1, name: 'Sảnh chờ' })
    expect(bodies).toEqual([{ code: 'ABCDEF' }])
  })
})

describe('the room code (AC3)', () => {
  it('shows the code big and copies the code and the invitation link', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')

    const code = await screen.findByRole('img', { name: 'M E L 8 8 9' })
    expect(code).toHaveTextContent('MEL889')

    await user.click(screen.getByRole('button', { name: 'Sao chép mã' }))
    expect(await navigator.clipboard.readText()).toBe('MEL889')
    await user.click(screen.getByRole('button', { name: 'Sao chép link' }))
    expect(await navigator.clipboard.readText()).toBe(`${location.origin}/r/MEL889`)
    expect(useToastStore.getState().toasts.map((t) => t.title)).toEqual([
      'Đã sao chép mã phòng',
      'Đã sao chép link mời',
    ])
  })

  it('says so when the browser refuses to copy', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await screen.findByRole('img', { name: 'M E L 8 8 9' })
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'))

    await user.click(screen.getByRole('button', { name: 'Sao chép mã' }))

    await waitFor(() => {
      expect(useToastStore.getState().toasts[0]).toMatchObject({
        title: 'Không sao chép được',
        tone: 'negative',
      })
    })
  })
})

describe('as a member (AC3)', () => {
  it('readies up and back, and sees the settings without being able to change them', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    const ready = screen.getByRole('button', { name: 'Sẵn sàng' })
    await user.click(ready)
    expect(kit.latest().commands('ready')).toEqual([{ ready: true }])

    // The button follows the server, not the click.
    expect(screen.getByRole('button', { name: 'Sẵn sàng' })).toBeInTheDocument()
    lobbyEvent({ type: 'PLAYER_READY_CHANGED', userId: GUEST_ID, isReady: true })
    await user.click(await screen.findByRole('button', { name: 'Bỏ sẵn sàng' }))
    expect(kit.latest().commands('ready')).toEqual([{ ready: true }, { ready: false }])

    expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Mời .* ra khỏi phòng$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đổi bộ nhạc' })).not.toBeInTheDocument()
    expect(screen.getByText('10 vòng')).toBeInTheDocument()
    expect(screen.getByText('5 người')).toBeInTheDocument()
  })

  it('shows who is ready and who is not', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    expect(screen.getByText('Chưa sẵn sàng')).toBeInTheDocument()
    lobbyEvent({ type: 'PLAYER_READY_CHANGED', userId: GUEST_ID, isReady: true })
    const row = screen.getByText('PlayerTwo').closest('li')
    expect(row).not.toBeNull()
    if (!row) return
    expect(await within(row).findByText('Sẵn sàng')).toBeInTheDocument()
  })

  it('shows a player score only when it is above zero', async () => {
    const scored = room({ players: [host({ score: 1240 }), player({ score: 0 })] })
    answerJoin(joinOk(scored))
    renderLobby('/r/MEL889')
    await goLive(scored)

    expect(screen.getByText('1.240 điểm')).toBeInTheDocument()
    expect(screen.getAllByText(/điểm$/)).toHaveLength(1)
  })

  it('follows people who join, leave and lose their connection', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    lobbyEvent({
      type: 'PLAYER_JOINED',
      user: player({ userId: THIRD_ID, nickname: 'Third' }),
    })
    expect(await screen.findByText('Third')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Người chơi (3/5)' })).toBeInTheDocument()

    lobbyEvent({ type: 'PLAYER_CONNECTION_CHANGED', userId: THIRD_ID, isConnected: false })
    expect(await screen.findByText('Mất kết nối')).toBeInTheDocument()

    lobbyEvent({ type: 'PLAYER_LEFT', userId: THIRD_ID, newHostId: HOST_ID, reason: 'VOLUNTARY' })
    await waitFor(() => {
      expect(screen.queryByText('Third')).not.toBeInTheDocument()
    })
  })

  it('shows changed settings the host made', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    lobbyEvent({
      type: 'ROOM_SETTINGS_CHANGED',
      roundCount: 15,
      maxPlayers: 3,
      playlistId: null,
      filter: { genreCodes: ['POP'], decades: [2020] },
    })

    expect(await screen.findByText('15 vòng')).toBeInTheDocument()
    expect(screen.getByText('3 người')).toBeInTheDocument()
    expect(screen.getByText('2 lựa chọn')).toBeInTheDocument()
  })

  it('shows the error the server gave for a refused command, until dismissed', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    act(() => {
      kit.latest().emit('/user/queue/errors', {
        type: 'ERROR',
        code: 'NOT_HOST',
        message: 'x',
        detail: null,
        serverTime: SERVER_TIME,
      })
    })
    expect(await screen.findByRole('alert')).toHaveTextContent('Chỉ chủ phòng làm được việc này.')
    await user.click(screen.getByRole('button', { name: 'Ẩn thông báo lỗi' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('leaves the room and goes home', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    await user.click(screen.getByRole('button', { name: 'Rời phòng' }))

    expect(kit.latest().commands('leave')).toEqual([{}])
    expect(await screen.findByText('trang chủ')).toBeInTheDocument()
    expect(useRoomStore.getState().room).toBeNull()
    await vi.waitFor(() => {
      expect(kit.latest().deactivated).toBe(true)
    })
  })

  it('mounts the chat stub in the lobby for this room', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    expect(await screen.findByTestId('room-chat')).toHaveTextContent(ROOM_ID)
  })
})

describe('as the host (AC3, BR-12)', () => {
  beforeEach(() => {
    signIn(HOST_ID, 'ThaiPO')
  })

  it('starts only when there are two players and the others are ready', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    const start = screen.getByRole('button', { name: 'Bắt đầu' })
    expect(start).toBeDisabled()
    expect(screen.getByText('Đang chờ PlayerTwo sẵn sàng.')).toBeInTheDocument()

    lobbyEvent({ type: 'PLAYER_READY_CHANGED', userId: GUEST_ID, isReady: true })
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeEnabled()
    })
    await user.click(screen.getByRole('button', { name: 'Bắt đầu' }))
    expect(kit.latest().commands('start')).toEqual([{}])
  })

  it('cannot start alone', async () => {
    answerJoin(joinOk(room({ players: [host()] })))
    renderLobby('/r/MEL889')
    await goLive(room({ players: [host()] }))

    expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeDisabled()
    expect(screen.getByText('Cần ít nhất 2 người để bắt đầu.')).toBeInTheDocument()
  })

  it('asks before removing a player, and removes only on confirm', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    await user.click(screen.getByRole('button', { name: 'Mời PlayerTwo ra khỏi phòng' }))
    const dialog = await screen.findByRole('dialog', { name: 'Mời PlayerTwo ra khỏi phòng?' })
    expect(dialog).toHaveAccessibleDescription('Người bị mời ra sẽ không vào lại phòng này được.')
    await user.click(within(dialog).getByRole('button', { name: 'Hủy' }))
    expect(kit.latest().commands('kick')).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Mời PlayerTwo ra khỏi phòng' }))
    await user.click(
      within(await screen.findByRole('dialog', { name: 'Mời PlayerTwo ra khỏi phòng?' })).getByRole(
        'button',
        { name: 'Mời ra' },
      ),
    )
    expect(kit.latest().commands('kick')).toEqual([{ targetUserId: GUEST_ID }])
  })

  it('has no remove button for the host themself', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()
    expect(screen.getAllByRole('button', { name: /^Mời .* ra khỏi phòng$/ })).toHaveLength(1)
  })

  it('changes rounds and the player cap with one command each', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    const rounds = screen.getByRole('group', { name: 'Số vòng' })
    await user.click(within(rounds).getByRole('radio', { name: '15' }))
    const players = screen.getByRole('group', { name: 'Số người tối đa' })
    await user.click(within(players).getByRole('radio', { name: '3' }))

    expect(kit.latest().commands('settings')).toEqual([{ roundCount: 15 }, { maxPlayers: 3 }])
  })

  it('cannot lower the cap below the players already inside', async () => {
    const three = room({ players: [host(), player(), player({ userId: THIRD_ID, nickname: 'T' })] })
    answerJoin(joinOk(three))
    renderLobby('/r/MEL889')
    await goLive(three)

    const players = screen.getByRole('group', { name: 'Số người tối đa' })
    expect(within(players).getByRole('radio', { name: '2' })).toBeDisabled()
    expect(within(players).getByRole('radio', { name: '3' })).toBeEnabled()
  })

  it('changes the music in a picker and applies it as a single command', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    await user.click(screen.getByRole('button', { name: 'Đổi bộ nhạc' }))
    await user.click(await screen.findByRole('button', { name: /V-pop thập niên 2010/ }))
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }))

    expect(kit.latest().commands('settings')).toEqual([
      { playlistId: '14f8a2c6-d570-4e9b-b3a8-c6e1d0f27a95' },
    ])
    expect(screen.queryByRole('button', { name: 'Áp dụng' })).not.toBeInTheDocument()
  })

  it('can leave the music alone after opening the picker', async () => {
    const user = userEvent.setup()
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    await user.click(screen.getByRole('button', { name: 'Đổi bộ nhạc' }))
    await user.click(screen.getByRole('button', { name: 'Hủy' }))

    expect(kit.latest().commands('settings')).toEqual([])
    expect(screen.getByRole('button', { name: 'Đổi bộ nhạc' })).toBeInTheDocument()
  })

  it('locks the controls while the countdown runs', async () => {
    answerJoin(joinOk(room({ players: [host(), player({ isReady: true })] })))
    renderLobby('/r/MEL889')
    await goLive(room({ players: [host(), player({ isReady: true })] }))

    lobbyEvent({ type: 'MATCH_STARTING', countdownSeconds: 5 })

    expect(await screen.findByRole('timer')).toHaveTextContent('5')
    expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Đổi bộ nhạc' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Mời .* ra khỏi phòng$/ })).not.toBeInTheDocument()
    lobbyEvent({ type: 'MATCH_COUNTDOWN', remainingSeconds: 3 })
    expect(await screen.findByText('3', { selector: '[role="timer"]' })).toBeInTheDocument()
  })

  it('keeps the host controls when a guest leaves and the server sends no new host', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    lobbyEvent({ type: 'PLAYER_LEFT', userId: GUEST_ID, newHostId: null, reason: 'DISCONNECTED' })

    await waitFor(() => {
      expect(screen.queryByText('PlayerTwo')).not.toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'M E L 8 8 9' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đổi bộ nhạc' })).toBeInTheDocument()
  })

  it('shows the host handover when the host leaves', async () => {
    signIn(GUEST_ID, 'PlayerTwo')
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()
    expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument()

    lobbyEvent({ type: 'PLAYER_LEFT', userId: HOST_ID, newHostId: GUEST_ID, reason: 'VOLUNTARY' })

    expect(await screen.findByRole('button', { name: 'Bắt đầu' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'M E L 8 8 9' })).toBeInTheDocument()
  })
})

describe('a matchmaking room without a host (BR-13)', () => {
  const quick = quickMatchRoom()
  const path = `/r/${quick.roomId}`

  beforeEach(() => {
    answerJoin(failWith(500, 'INTERNAL_ERROR'))
  })

  it('hides settings, removal, start and copy, and shows the wait instead', async () => {
    renderLobby(path, { room: quick })
    await goLive(quick)

    expect(screen.getByText('Ghép trận nhanh')).toBeInTheDocument()
    for (const name of ['Bắt đầu', 'Sẵn sàng', 'Sao chép mã', 'Sao chép link', 'Đổi bộ nhạc']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
    }
    expect(screen.queryByRole('group', { name: 'Số vòng' })).not.toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /M E L/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Chưa sẵn sàng')).not.toBeInTheDocument()
    expect(screen.getByText(/Đang chờ thêm người chơi \(1\/5\)/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Rời phòng' })).toBeEnabled()
  })

  it('shows the countdown the server runs', async () => {
    renderLobby(path, { room: quick })
    await goLive(quick)

    lobbyEvent({ type: 'MATCH_COUNTDOWN', remainingSeconds: 28 })
    expect(await screen.findByRole('timer')).toHaveTextContent('28')

    lobbyEvent({ type: 'MATCH_COUNTDOWN_STOPPED', reason: 'PLAYER_LEFT' })
    expect(await screen.findByText('Có người rời đi nên đếm ngược đã dừng.')).toBeInTheDocument()
    expect(screen.queryByRole('timer')).not.toBeInTheDocument()
  })

  it('does not offer the solo hint in a room with a host, even if the event arrives', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    lobbyEvent({ type: 'MATCH_SOLO_SUGGESTION', message: 'gợi ý' })

    await waitFor(() => {
      expect(useRoomStore.getState().soloSuggestion).toBe('gợi ý')
    })
    expect(screen.queryByRole('link', { name: 'Chơi đơn' })).not.toBeInTheDocument()
  })

  it('offers solo play or a private room after the 60 s hint', async () => {
    renderLobby(path, { room: quick })
    await goLive(quick)

    lobbyEvent({ type: 'MATCH_SOLO_SUGGESTION', message: 'gợi ý' })

    expect(await screen.findByRole('link', { name: 'Chơi đơn' })).toHaveAttribute('href', '/solo')
    expect(screen.getByRole('link', { name: 'Tạo phòng riêng' })).toHaveAttribute(
      'href',
      '/rooms/new',
    )
  })
})

describe('the link to the server (AC4)', () => {
  it('warns while connecting and while reconnecting, and clears once a snapshot arrives', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await screen.findByRole('heading', { level: 1, name: 'Sảnh chờ' })

    expect(screen.getByRole('status', { name: '' })).toHaveTextContent('Đang kết nối với phòng')
    await goLive()
    expect(screen.queryByText(/Đang kết nối với phòng/)).not.toBeInTheDocument()

    act(() => {
      kit.latest().drop()
    })
    expect(await screen.findByText(/Mất kết nối, đang kết nối lại/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sẵn sàng' })).toBeDisabled()

    act(() => {
      kit.latest().connect()
    })
    expect(await screen.findByText(/Đang kết nối với phòng/)).toBeInTheDocument()
    expect(kit.latest().commands('resync')).toHaveLength(2)

    act(() => {
      kit.latest().emit('/user/queue/private', snapshot(room({ roundCount: 5 })))
    })
    await waitFor(() => {
      expect(screen.queryByRole('status', { name: '' })).not.toBeInTheDocument()
    })
    expect(screen.getByText('5 vòng')).toBeInTheDocument()
  })
})

describe('leaving the lobby for the match or out of the room', () => {
  it('goes to the match when it starts while the player is here', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    lobbyEvent({ type: 'MATCH_STARTING', countdownSeconds: 5 })
    act(() => {
      kit.latest().emit(ROUND, {
        type: 'ROUND_PREPARE',
        roundNo: 1,
        totalRounds: 10,
        timeoutMs: 5000,
        serverTime: SERVER_TIME,
      })
    })

    expect(await screen.findByText('trang chơi')).toBeInTheDocument()
  })

  it('stays in the lobby when a round is prepared for a match it never saw start (BR-15)', async () => {
    answerJoin(joinOk(room({ status: 'PLAYING' })))
    renderLobby('/r/MEL889')
    await goLive(room({ status: 'PLAYING' }))

    act(() => {
      kit.latest().emit(ROUND, {
        type: 'ROUND_PREPARE',
        roundNo: 2,
        totalRounds: 10,
        timeoutMs: 5000,
        serverTime: SERVER_TIME,
      })
    })

    expect(screen.queryByText('trang chơi')).not.toBeInTheDocument()
    expect(screen.getByText('Trận đang diễn ra, bạn sẽ chơi từ trận sau')).toBeInTheDocument()
  })

  it('shows the waiting list while a match runs', async () => {
    answerJoin(joinOk(room({ status: 'PLAYING' })))
    renderLobby('/r/MEL889')
    await goLive(room({ status: 'PLAYING' }))

    lobbyEvent({
      type: 'WAITING_LIST_CHANGED',
      waitingPlayers: [player({ userId: THIRD_ID, nickname: 'LateJoiner' })],
    })

    expect(await screen.findByRole('heading', { name: 'Chờ trận sau (1)' })).toBeInTheDocument()
    expect(screen.getByText('LateJoiner')).toBeInTheDocument()
  })

  it('tells a removed player so and drops the socket', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    lobbyEvent({ type: 'PLAYER_KICKED', userId: GUEST_ID })

    expect(
      await screen.findByRole('heading', { name: 'Bạn đã bị mời ra khỏi phòng' }),
    ).toBeInTheDocument()
    await vi.waitFor(() => {
      expect(kit.latest().deactivated).toBe(true)
    })
  })

  it('returns to a fresh lobby after the match', async () => {
    answerJoin(joinOk())
    renderLobby('/r/MEL889')
    await goLive()

    lobbyEvent({ type: 'MATCH_STARTING', countdownSeconds: 5 })
    expect(await screen.findByRole('timer')).toBeInTheDocument()
    lobbyEvent({ type: 'RETURN_TO_LOBBY' })

    await waitFor(() => {
      expect(screen.queryByRole('timer')).not.toBeInTheDocument()
    })
  })
})
