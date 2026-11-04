import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { topicDestination } from './commands'
import { useRoomRealtime } from './useRoomRealtime'
import { useRoomStore } from './roomStore'
import { installFakeStomp, type FakeStompKit } from './testing/fakeStomp'
import { GUEST_ID, ROOM_ID, SERVER_TIME, room } from './testing/fixtures'

let kit: FakeStompKit

const snapshot = (overrides: Partial<ReturnType<typeof room>> = {}) => ({
  type: 'RESYNC_STATE' as const,
  room: room(overrides),
  roomPhase: 'LOBBY' as const,
  currentRound: null,
  scores: [],
  lastRoundAnswer: null,
  lastRoundResults: null,
  serverTime: SERVER_TIME,
})

beforeEach(() => {
  kit = installFakeStomp()
  useRoomStore.getState().reset()
})
afterEach(() => {
  kit.uninstall()
  vi.useRealTimers()
})

describe('useRoomRealtime', () => {
  it('shows the REST snapshot at once, then waits for the server snapshot before saying "live"', () => {
    const { result } = renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))

    expect(useRoomStore.getState().room?.code).toBe('MEL889')
    expect(result.current.link).toBe('connecting')

    act(() => {
      kit.latest().connect()
    })
    expect(result.current.link).toBe('syncing')
    expect(kit.latest().commands('resync')).toHaveLength(1)

    act(() => {
      kit.latest().emit('/user/queue/private', snapshot({ roundCount: 5 }))
    })
    expect(result.current.link).toBe('live')
    expect(useRoomStore.getState().room?.roundCount).toBe(5)
  })

  it('feeds lobby events into the store', () => {
    renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))
    act(() => {
      kit.latest().connect()
      kit.latest().emit(topicDestination(ROOM_ID, 'lobby'), {
        type: 'PLAYER_READY_CHANGED',
        userId: GUEST_ID,
        isReady: true,
        serverTime: SERVER_TIME,
      })
    })
    expect(useRoomStore.getState().room?.players.find((p) => p.userId === GUEST_ID)?.isReady).toBe(
      true,
    )
  })

  it('reconnects: shows "reconnecting", subscribes again and asks for a fresh snapshot', () => {
    const { result } = renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))
    const client = kit.latest()
    act(() => {
      client.connect()
      client.emit('/user/queue/private', snapshot())
    })
    expect(result.current.link).toBe('live')

    act(() => {
      client.drop()
    })
    expect(result.current.link).toBe('reconnecting')
    expect(client.isSubscribed(topicDestination(ROOM_ID, 'lobby'))).toBe(false)

    act(() => {
      client.connect()
    })
    expect(client.isSubscribed(topicDestination(ROOM_ID, 'lobby'))).toBe(true)
    expect(client.isSubscribed(topicDestination(ROOM_ID, 'round'))).toBe(true)
    expect(client.isSubscribed('/user/queue/private')).toBe(true)
    expect(client.commands('resync')).toHaveLength(2)
    expect(result.current.link).toBe('syncing')
  })

  it('asks for the snapshot again when the first answer never comes', () => {
    vi.useFakeTimers()
    renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))
    act(() => {
      kit.latest().connect()
    })
    expect(kit.latest().commands('resync')).toHaveLength(1)
    act(() => {
      vi.advanceTimersByTime(4_000)
    })
    expect(kit.latest().commands('resync')).toHaveLength(2)
  })

  it('ignores a snapshot of another room delivered on the shared private queue', () => {
    renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))
    act(() => {
      kit.latest().connect()
      kit.latest().emit('/user/queue/private', {
        ...snapshot(),
        room: room({ roomId: 'ffffffff-0000-0000-0000-000000000000', roundCount: 15 }),
      })
    })
    expect(useRoomStore.getState().room?.roundCount).toBe(10)
    expect(useRoomStore.getState().link).toBe('syncing')
  })

  it('sends commands only while connected', () => {
    const { result } = renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))
    expect(result.current.send('ready', { ready: true })).toBe(false)

    act(() => {
      kit.latest().connect()
    })
    expect(result.current.send('ready', { ready: true })).toBe(true)
    expect(kit.latest().published.at(-1)).toEqual({
      destination: `/app/rooms/${ROOM_ID}/ready`,
      body: { ready: true },
    })

    act(() => {
      kit.latest().drop()
    })
    expect(result.current.send('ready', { ready: false })).toBe(false)
  })

  it('delivers the topics a screen asked for to its handler, without touching the store twice', () => {
    const onMessage = vi.fn()
    renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room(), topics: ['chat'], onMessage }))
    const chat = {
      type: 'CHAT_MESSAGE',
      messageId: 'm1',
      senderId: GUEST_ID,
      senderNickname: 'PlayerTwo',
      content: 'xin chào',
      sentAt: SERVER_TIME,
      serverTime: SERVER_TIME,
    }
    act(() => {
      kit.latest().connect()
      kit.latest().emit(topicDestination(ROOM_ID, 'chat'), chat)
    })
    expect(onMessage).toHaveBeenCalledExactlyOnceWith(chat)
  })

  it('shares one connection between screens of the same room and closes it with the last one', async () => {
    const first = renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))
    const second = renderHook(() => useRoomRealtime(ROOM_ID))
    expect(kit.clients).toHaveLength(1)

    first.unmount()
    await new Promise((resolve) => setTimeout(resolve, 5))
    expect(kit.latest().deactivated).toBe(false)

    second.unmount()
    await vi.waitFor(() => {
      expect(kit.latest().deactivated).toBe(true)
    })
  })

  it('keeps the connection when a screen unmounts and mounts again at once (StrictMode)', async () => {
    const first = renderHook(() => useRoomRealtime(ROOM_ID, { initialRoom: room() }))
    first.unmount()
    renderHook(() => useRoomRealtime(ROOM_ID))
    await new Promise((resolve) => setTimeout(resolve, 5))
    expect(kit.clients).toHaveLength(1)
    expect(kit.latest().deactivated).toBe(false)
  })

  it('does nothing without a room id', () => {
    const { result } = renderHook(() => useRoomRealtime(null))
    expect(kit.clients).toHaveLength(0)
    expect(result.current.send('ready', { ready: true })).toBe(false)
  })
})
