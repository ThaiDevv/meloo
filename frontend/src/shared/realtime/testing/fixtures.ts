import type { RoomPlayerSummary, RoomResponse } from '../messages'

export const HOST_ID = '11111111-2222-3333-4444-555555555555'
export const GUEST_ID = '22222222-3333-4444-5555-666666666666'
export const THIRD_ID = '33333333-4444-5555-6666-777777777777'
export const ROOM_ID = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d'

export function player(overrides: Partial<RoomPlayerSummary> = {}): RoomPlayerSummary {
  return {
    userId: GUEST_ID,
    nickname: 'PlayerTwo',
    avatarUrl: null,
    isHost: false,
    isReady: false,
    isGuest: true,
    isConnected: true,
    score: 0,
    ...overrides,
  }
}

export function host(overrides: Partial<RoomPlayerSummary> = {}): RoomPlayerSummary {
  return player({
    userId: HOST_ID,
    nickname: 'ThaiPO',
    isHost: true,
    isReady: true,
    isGuest: false,
    ...overrides,
  })
}

/** A private room in the lobby with its host and one guest, like contracts/examples/room/join-room.200.json. */
export function room(overrides: Partial<RoomResponse> = {}): RoomResponse {
  const players = overrides.players ?? [host(), player()]
  return {
    roomId: ROOM_ID,
    code: 'MEL889',
    roomType: 'PRIVATE_ROOM',
    hostId: HOST_ID,
    status: 'LOBBY',
    roundCount: 10,
    maxPlayers: 5,
    currentRound: 0,
    playerCount: players.length,
    players,
    playlistId: '4a7c1b23-5e89-4d66-a3f1-0c9b8a7d6e54',
    filter: null,
    createdAt: '2026-10-29T10:00:00Z',
    ...overrides,
  }
}

/** A matchmaking room: no code, no host (BR-13). */
export function quickMatchRoom(overrides: Partial<RoomResponse> = {}): RoomResponse {
  const players = overrides.players ?? [player({ userId: GUEST_ID, nickname: 'PlayerTwo' })]
  return room({
    code: null,
    hostId: null,
    roomType: 'QUICK_MATCH',
    playlistId: null,
    players,
    ...overrides,
  })
}

export const SERVER_TIME = '2026-10-29T10:00:00Z'
