import { describe, expect, it } from 'vitest'
import type { ServerRealtimeMessage } from './messages'
import { EMPTY_ROOM_DATA, reduceRoomMessage, type RoomData } from './roomReducer'
import {
  GUEST_ID,
  HOST_ID,
  SERVER_TIME,
  THIRD_ID,
  host,
  player,
  quickMatchRoom,
  room,
} from './testing/fixtures'

function apply(data: RoomData, message: ServerRealtimeMessage): RoomData {
  return { ...data, ...reduceRoomMessage(data, message) }
}

const inLobby: RoomData = { ...EMPTY_ROOM_DATA, room: room() }

describe('reduceRoomMessage: players', () => {
  it('adds a joined player and keeps playerCount equal to the list', () => {
    const next = apply(inLobby, {
      type: 'PLAYER_JOINED',
      user: player({ userId: THIRD_ID, nickname: 'Third' }),
      serverTime: SERVER_TIME,
    })
    expect(next.room?.players.map((p) => p.nickname)).toEqual(['ThaiPO', 'PlayerTwo', 'Third'])
    expect(next.room?.playerCount).toBe(3)
  })

  it('does not list a player twice when the join event repeats', () => {
    const joined: ServerRealtimeMessage = {
      type: 'PLAYER_JOINED',
      user: player({ nickname: 'PlayerTwo again' }),
      serverTime: SERVER_TIME,
    }
    const next = apply(inLobby, joined)
    expect(next.room?.players).toHaveLength(2)
    expect(next.room?.players.at(-1)?.nickname).toBe('PlayerTwo again')
  })

  it('passes the host role on when the host leaves', () => {
    const next = apply(inLobby, {
      type: 'PLAYER_LEFT',
      userId: HOST_ID,
      newHostId: GUEST_ID,
      reason: 'VOLUNTARY',
      serverTime: SERVER_TIME,
    })
    expect(next.room?.hostId).toBe(GUEST_ID)
    expect(next.room?.players).toEqual([
      expect.objectContaining({ userId: GUEST_ID, isHost: true }),
    ])
    expect(next.room?.playerCount).toBe(1)
  })

  it('keeps the host when someone else leaves with a null newHostId, as the contract example does', () => {
    const next = apply(inLobby, {
      type: 'PLAYER_LEFT',
      userId: GUEST_ID,
      newHostId: null,
      reason: 'DISCONNECTED',
      serverTime: SERVER_TIME,
    })
    expect(next.room?.hostId).toBe(HOST_ID)
    expect(next.room?.players).toEqual([expect.objectContaining({ userId: HOST_ID, isHost: true })])
  })

  it('leaves no host when the host leaves and the server names nobody', () => {
    const next = apply(inLobby, {
      type: 'PLAYER_LEFT',
      userId: HOST_ID,
      newHostId: null,
      reason: 'TIMEOUT',
      serverTime: SERVER_TIME,
    })
    expect(next.room?.hostId).toBeNull()
  })

  it('never promotes anyone in a room without a host (matchmaking)', () => {
    const quick: RoomData = {
      ...EMPTY_ROOM_DATA,
      room: quickMatchRoom({ players: [player(), player({ userId: THIRD_ID })] }),
    }
    const next = apply(quick, {
      type: 'PLAYER_LEFT',
      userId: THIRD_ID,
      newHostId: GUEST_ID,
      reason: 'TIMEOUT',
      serverTime: SERVER_TIME,
    })
    expect(next.room?.hostId).toBeNull()
    expect(next.room?.players.some((p) => p.isHost)).toBe(false)
  })

  it('updates connection and ready state of one player only', () => {
    let next = apply(inLobby, {
      type: 'PLAYER_READY_CHANGED',
      userId: GUEST_ID,
      isReady: true,
      serverTime: SERVER_TIME,
    })
    next = apply(next, {
      type: 'PLAYER_CONNECTION_CHANGED',
      userId: GUEST_ID,
      isConnected: false,
      serverTime: SERVER_TIME,
    })
    expect(next.room?.players.find((p) => p.userId === GUEST_ID)).toMatchObject({
      isReady: true,
      isConnected: false,
    })
    expect(next.room?.players.find((p) => p.userId === HOST_ID)).toMatchObject({
      isReady: true,
      isConnected: true,
    })
  })

  it('removes a kicked player and remembers who was kicked', () => {
    const next = apply(inLobby, {
      type: 'PLAYER_KICKED',
      userId: GUEST_ID,
      serverTime: SERVER_TIME,
    })
    expect(next.room?.players.map((p) => p.userId)).toEqual([HOST_ID])
    expect(next.kickedUserIds).toEqual([GUEST_ID])
  })
})

describe('reduceRoomMessage: settings and countdown', () => {
  it('applies new settings, including a filter that replaces the playlist', () => {
    const next = apply(inLobby, {
      type: 'ROOM_SETTINGS_CHANGED',
      roundCount: 15,
      maxPlayers: 3,
      playlistId: null,
      filter: { genreCodes: ['POP'] },
      serverTime: SERVER_TIME,
    })
    expect(next.room).toMatchObject({
      roundCount: 15,
      maxPlayers: 3,
      playlistId: null,
      filter: { genreCodes: ['POP'] },
    })
  })

  it('follows a countdown from start to stop', () => {
    let next = apply(inLobby, {
      type: 'MATCH_STARTING',
      countdownSeconds: 5,
      serverTime: SERVER_TIME,
    })
    expect(next).toMatchObject({ phase: 'COUNTDOWN', countdown: 5, matchStarting: true })
    next = apply(next, { type: 'MATCH_COUNTDOWN', remainingSeconds: 3, serverTime: SERVER_TIME })
    expect(next.countdown).toBe(3)
    next = apply(next, {
      type: 'MATCH_COUNTDOWN_STOPPED',
      reason: 'PLAYER_LEFT',
      serverTime: SERVER_TIME,
    })
    expect(next).toMatchObject({
      phase: 'LOBBY',
      countdown: null,
      countdownStoppedReason: 'PLAYER_LEFT',
      matchStarting: false,
    })
  })

  it('keeps the solo hint and the waiting list as the server sends them', () => {
    let next = apply(inLobby, {
      type: 'MATCH_SOLO_SUGGESTION',
      message: 'Chơi đơn?',
      serverTime: SERVER_TIME,
    })
    next = apply(next, {
      type: 'WAITING_LIST_CHANGED',
      waitingPlayers: [player({ userId: THIRD_ID })],
      serverTime: SERVER_TIME,
    })
    expect(next.soloSuggestion).toBe('Chơi đơn?')
    expect(next.waitingPlayers.map((p) => p.userId)).toEqual([THIRD_ID])
  })

  it('counts ROUND_PREPARE and marks the room as playing', () => {
    const next = apply(inLobby, {
      type: 'ROUND_PREPARE',
      roundNo: 1,
      totalRounds: 10,
      timeoutMs: 5000,
      serverTime: SERVER_TIME,
    })
    expect(next).toMatchObject({ phase: 'ROUND_PREPARE', roundPrepareCount: 1 })
    expect(next.room).toMatchObject({ status: 'PLAYING', currentRound: 1 })
  })

  it('returns the room to the lobby after a match', () => {
    const playing: RoomData = {
      ...inLobby,
      phase: 'FINISHED',
      matchStarting: true,
      waitingPlayers: [player({ userId: THIRD_ID })],
      room: room({ status: 'PLAYING', currentRound: 10 }),
    }
    const next = apply(playing, { type: 'RETURN_TO_LOBBY', serverTime: SERVER_TIME })
    expect(next).toMatchObject({ phase: 'LOBBY', matchStarting: false, waitingPlayers: [] })
    expect(next.room).toMatchObject({ status: 'LOBBY', currentRound: 0 })
  })
})

describe('reduceRoomMessage: snapshots and noise', () => {
  it('replaces the whole room with a snapshot and drops a stale countdown in the lobby', () => {
    const stale: RoomData = { ...inLobby, countdown: 4, matchStarting: true }
    const next = apply(stale, {
      type: 'RESYNC_STATE',
      room: room({ roundCount: 5 }),
      roomPhase: 'LOBBY',
      currentRound: null,
      scores: [],
      lastRoundAnswer: null,
      lastRoundResults: null,
      serverTime: SERVER_TIME,
    })
    expect(next.room?.roundCount).toBe(5)
    expect(next).toMatchObject({ phase: 'LOBBY', countdown: null, matchStarting: false })
  })

  it('marks a snapshot taken during the countdown as "this match is starting for me"', () => {
    const next = apply(inLobby, {
      type: 'RESYNC_STATE',
      room: room(),
      roomPhase: 'COUNTDOWN',
      currentRound: null,
      scores: [],
      lastRoundAnswer: null,
      lastRoundResults: null,
      serverTime: SERVER_TIME,
    })
    expect(next).toMatchObject({ phase: 'COUNTDOWN', matchStarting: true })
  })

  it('stores the last error of a command', () => {
    const error: ServerRealtimeMessage = {
      type: 'ERROR',
      code: 'NOT_HOST',
      message: 'Chỉ chủ phòng',
      detail: null,
      serverTime: SERVER_TIME,
    }
    expect(apply(inLobby, error).lastError).toEqual(error)
  })

  it('ignores room edits when there is no room yet, and messages that are not about the room', () => {
    expect(
      reduceRoomMessage(EMPTY_ROOM_DATA, {
        type: 'PLAYER_READY_CHANGED',
        userId: GUEST_ID,
        isReady: true,
        serverTime: SERVER_TIME,
      }),
    ).toBeNull()
    expect(
      reduceRoomMessage(inLobby, {
        type: 'CHAT_MESSAGE',
        messageId: 'm',
        senderId: GUEST_ID,
        senderNickname: 'x',
        content: 'hi',
        sentAt: SERVER_TIME,
        serverTime: SERVER_TIME,
      }),
    ).toBeNull()
  })

  it('does not mutate the previous state', () => {
    const before = structuredClone(inLobby)
    apply(inLobby, { type: 'PLAYER_KICKED', userId: GUEST_ID, serverTime: SERVER_TIME })
    expect(inLobby).toEqual(before)
    expect(host().isHost).toBe(true)
  })
})
