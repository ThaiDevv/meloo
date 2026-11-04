import { waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '@/shared/api/authStore'
import { commandDestination } from './commands'
import {
  addMockPlayer,
  installMockRealtime,
  joinMockCompanion,
  openMockRoom,
  readyMockCompanion,
  resetMockRealtime,
} from './mockRoomServer'
import { acquireRoomSocket } from './roomConnection'
import { useRoomStore } from './roomStore'
import { GUEST_ID, HOST_ID, host, player, room, ROOM_ID } from './testing/fixtures'

const me = {
  accessToken: 'mock-access-token-1',
  tokenType: 'Bearer' as const,
  expiresAt: '2030-01-01T00:00:00Z',
  user: {
    id: HOST_ID,
    kind: 'REGISTERED' as const,
    role: 'USER' as const,
    nickname: 'ThaiPO',
    createdAt: '2026-10-23T13:10:00Z',
  },
}

function send(command: Parameters<typeof commandDestination>[1], body: unknown) {
  const { socket } = held
  socket.publish(commandDestination(ROOM_ID, command), body)
}

let held: ReturnType<typeof acquireRoomSocket>
let uninstall: () => void

async function connect() {
  useRoomStore.getState().open(ROOM_ID, null)
  held = acquireRoomSocket(ROOM_ID)
  await waitFor(() => {
    expect(useRoomStore.getState().link).toBe('live')
  })
}

describe('mock room broker', () => {
  beforeEach(() => {
    resetMockRealtime()
    useAuthStore.getState().setSession(me)
    uninstall = installMockRealtime({ connectDelayMs: 0, companionDelayMs: null })
    openMockRoom(room({ players: [host()] }))
  })

  afterEach(() => {
    held.release()
    uninstall()
    useAuthStore.getState().clear()
    resetMockRealtime()
  })

  it('answers the resync of a connection with the room, so the lobby shows its players', async () => {
    await connect()
    const state = useRoomStore.getState()
    expect(state.phase).toBe('LOBBY')
    expect(state.room?.players.map((p) => p.nickname)).toEqual(['ThaiPO'])
    expect(state.room?.hostId).toBe(HOST_ID)
  })

  it('broadcasts a player who joins and the ready flag they set', async () => {
    await connect()
    joinMockCompanion(ROOM_ID)
    await waitFor(() => {
      expect(useRoomStore.getState().room?.players).toHaveLength(2)
    })
    readyMockCompanion(ROOM_ID)
    await waitFor(() => {
      expect(useRoomStore.getState().room?.players[1]?.isReady).toBe(true)
    })
  })

  it('applies the ready command of the player', async () => {
    openMockRoom(room({ players: [host({ isReady: false })] }))
    await connect()
    send('ready', { ready: true })
    await waitFor(() => {
      expect(useRoomStore.getState().room?.players[0]?.isReady).toBe(true)
    })
  })

  it('applies settings sent by the host and refuses them from a guest', async () => {
    await connect()
    send('settings', { roundCount: 15, maxPlayers: 3 })
    await waitFor(() => {
      expect(useRoomStore.getState().room).toMatchObject({ roundCount: 15, maxPlayers: 3 })
    })
    useAuthStore.getState().setSession({ ...me, user: { ...me.user, id: GUEST_ID } })
    addMockPlayer(ROOM_ID, player({ userId: GUEST_ID }))
    held.release()
    uninstall()
    uninstall = installMockRealtime({ connectDelayMs: 0, companionDelayMs: null })
    await connect()
    send('settings', { roundCount: 5 })
    await waitFor(() => {
      expect(useRoomStore.getState().lastError?.code).toBe('NOT_HOST')
    })
    expect(useRoomStore.getState().room?.roundCount).toBe(15)
  })

  it('kicks a player, and remembers it', async () => {
    addMockPlayer(ROOM_ID, player())
    await connect()
    send('kick', { targetUserId: GUEST_ID })
    await waitFor(() => {
      expect(useRoomStore.getState().kickedUserIds).toContain(GUEST_ID)
    })
    expect(useRoomStore.getState().room?.players.map((p) => p.userId)).toEqual([HOST_ID])
  })

  it('hands the room to the next player when the host leaves', async () => {
    addMockPlayer(ROOM_ID, player())
    await connect()
    send('leave', {})
    await waitFor(() => {
      expect(useRoomStore.getState().room?.hostId).toBe(GUEST_ID)
    })
  })

  it('says why a match cannot start', async () => {
    await connect()
    send('start', {})
    await waitFor(() => {
      expect(useRoomStore.getState().lastError).toMatchObject({
        code: 'ROOM_CANNOT_START',
        startFailureReason: 'NOT_ENOUGH_PLAYERS',
      })
    })
  })

  it('tells a connection that is not in the room', async () => {
    useAuthStore.getState().setSession({ ...me, user: { ...me.user, id: GUEST_ID } })
    useRoomStore.getState().open(ROOM_ID, null)
    held = acquireRoomSocket(ROOM_ID)
    await waitFor(() => {
      expect(useRoomStore.getState().lastError?.code).toBe('NOT_IN_ROOM')
    })
  })
})
