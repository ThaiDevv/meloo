import { http, HttpResponse } from 'msw'
import { useAuthStore } from '@/shared/api/authStore'
import type { RoomPlayerSummary, RoomResponse } from '@/shared/realtime/messages'
import {
  addMockPlayer,
  findMockRoomByCode,
  hasBeenKicked,
  openMockRoom,
  resetMockRealtime,
} from '@/shared/realtime/mockRoomServer'
import createFilterExample from '../../../../../contracts/examples/room/create-room.filter.201.json'
import createExample from '../../../../../contracts/examples/room/create-room.201.json'
import joinExample from '../../../../../contracts/examples/room/join-room.200.json'
import listPublicExample from '../../../../../contracts/examples/room/list-public-rooms.200.json'
import filterConflictExample from '../../../../../contracts/examples/room/problem.filter-conflict.400.json'
import notEnoughTracksExample from '../../../../../contracts/examples/room/problem.not-enough-tracks.422.json'
import playlistNotFoundExample from '../../../../../contracts/examples/room/problem.playlist-not-found.404.json'
import roomFullExample from '../../../../../contracts/examples/room/problem.room-full.409.json'
import roomKickedExample from '../../../../../contracts/examples/room/problem.room-kicked.403.json'
import roomNotFoundExample from '../../../../../contracts/examples/room/problem.room-not-found.404.json'
import quickMatchExample from '../../../../../contracts/examples/room/quick-match.200.json'
import unauthorizedExample from '../../../../../contracts/examples/common/problem.unauthorized.401.json'
import validationExample from '../../../../../contracts/examples/common/problem.validation-failed.400.json'

const PROBLEM = { 'Content-Type': 'application/problem+json' }

/**
 * Deterministic triggers for the error branches (mock mode and tests only):
 * - join code `FULL22` answers 409 ROOM_FULL, `KICK22` answers 403 ROOM_KICKED, any code no mock
 *   room has answers 404 ROOM_NOT_FOUND;
 * - a playlistId of `00000000-0000-0000-0000-000000000404` answers 404 PLAYLIST_NOT_FOUND and
 *   `00000000-0000-0000-0000-000000000422` answers 422 NOT_ENOUGH_TRACKS;
 * - a body with both `playlistId` and `filter` answers 400 (filter conflict).
 */
export const FULL_ROOM_CODE = 'FULL22'
export const KICKED_ROOM_CODE = 'KICK22'
export const MISSING_PLAYLIST_ID = '00000000-0000-0000-0000-000000000404'
export const SHORT_PLAYLIST_ID = '00000000-0000-0000-0000-000000000422'

/** For tests: forget every mock room and connection. */
export function resetRoomMock(): void {
  resetMockRealtime()
  issued = 0
}

let issued = 0

function nextId(prefix: string): string {
  issued++
  return `${prefix}-0000-4000-8000-${String(issued).padStart(12, '0')}`
}

function problem(body: object, status: number): Response {
  return HttpResponse.json(body, { status, headers: PROBLEM })
}

/** The signed-in mock guest as a room player, or null when nobody is signed in. */
function currentPlayer(): RoomPlayerSummary | null {
  const user = useAuthStore.getState().user
  if (!user) return null
  return {
    userId: user.id,
    nickname: user.nickname,
    avatarUrl: null,
    isHost: false,
    isReady: false,
    isGuest: user.kind === 'GUEST',
    isConnected: true,
    score: 0,
  }
}

function newCode(): string {
  const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
  let code = ''
  for (let i = 0; i < 6; i++) code += alphabet.charAt(Math.floor(Math.random() * alphabet.length))
  return code
}

/** The shape of a room in contracts/examples/room/*.json, before its enums are narrowed. */
interface ExampleRoom {
  roomId: string
  code?: string | null
  roomType: string
  hostId?: string | null
  roundCount: number
  maxPlayers: number
  currentRound: number
  players: {
    userId: string
    nickname: string
    avatarUrl?: string | null
    isHost: boolean
    isReady: boolean
    isGuest: boolean
    isConnected: boolean
    score: number
  }[]
  playlistId?: string | null
  filter?: RoomResponse['filter']
  createdAt: string
}

function roomTypeOf(value: string): RoomResponse['roomType'] {
  if (value === 'PUBLIC_ROOM' || value === 'QUICK_MATCH') return value
  return 'PRIVATE_ROOM'
}

/** A lobby-phase room built from an example: nothing is typed in by hand. */
function toRoom(room: ExampleRoom): RoomResponse {
  const players = room.players.map((p) => ({ ...p, avatarUrl: p.avatarUrl ?? null }))
  return {
    ...room,
    code: room.code ?? null,
    roomType: roomTypeOf(room.roomType),
    hostId: room.hostId ?? null,
    status: 'LOBBY',
    roundCount: roundCountOf(room.roundCount, 10),
    currentRound: 0,
    playerCount: players.length,
    players,
    playlistId: room.playlistId ?? null,
    filter: room.filter ?? null,
  }
}

async function readBody(request: Request): Promise<Record<string, unknown>> {
  const body: unknown = await request.json().catch(() => null)
  return typeof body === 'object' && body !== null ? Object.fromEntries(Object.entries(body)) : {}
}

/** Shared by create and quick match: the playlist triggers and the filter conflict. */
function rejectSource(body: Record<string, unknown>): Response | null {
  if (body.playlistId === MISSING_PLAYLIST_ID) return problem(playlistNotFoundExample, 404)
  if (body.playlistId === SHORT_PLAYLIST_ID) return problem(notEnoughTracksExample, 422)
  if (body.playlistId && body.filter) return problem(filterConflictExample, 400)
  return null
}

function roundCountOf(value: unknown, fallback: 5 | 10 | 15): 5 | 10 | 15 {
  return value === 5 || value === 10 || value === 15 ? value : fallback
}

/** Room endpoints answered from contracts/examples/room/**. */
export const roomHandlers = [
  http.post('*/api/v1/rooms', async ({ request }) => {
    const me = currentPlayer()
    if (!me) return problem(unauthorizedExample, 401)
    const body = await readBody(request)
    const rejected = rejectSource(body)
    if (rejected) return rejected
    const base = body.filter ? createFilterExample : createExample
    const host: RoomPlayerSummary = { ...me, isHost: true, isReady: true }
    const room: RoomResponse = {
      ...toRoom(base),
      roomId: nextId('00000000'),
      code: newCode(),
      roomType: body.roomType === 'PUBLIC_ROOM' ? 'PUBLIC_ROOM' : 'PRIVATE_ROOM',
      hostId: host.userId,
      roundCount: roundCountOf(body.roundCount, 10),
      maxPlayers: typeof body.maxPlayers === 'number' ? body.maxPlayers : base.maxPlayers,
      players: [host],
      playerCount: 1,
      createdAt: new Date().toISOString(),
    }
    openMockRoom(room, true)
    return HttpResponse.json(room, { status: 201 })
  }),
  http.post('*/api/v1/rooms/join', async ({ request }) => {
    const me = currentPlayer()
    if (!me) return problem(unauthorizedExample, 401)
    const { code } = await readBody(request)
    if (typeof code !== 'string' || code.length !== 6) return problem(validationExample, 400)
    if (code === FULL_ROOM_CODE) return problem(roomFullExample, 409)
    if (code === KICKED_ROOM_CODE) return problem(roomKickedExample, 403)
    let known = findMockRoomByCode(code)
    if (!known && code === joinExample.code) {
      // The example room, with the signed-in guest taking the seat of its second player.
      known = toRoom(joinExample)
      known = { ...known, players: known.players.slice(0, 1), playerCount: 1 }
      openMockRoom(known)
    }
    if (!known) return problem(roomNotFoundExample, 404)
    if (hasBeenKicked(known.roomId, me.userId)) return problem(roomKickedExample, 403)
    if (known.players.length >= known.maxPlayers) return problem(roomFullExample, 409)
    const joined = addMockPlayer(known.roomId, me)
    return HttpResponse.json(joined ?? known)
  }),
  http.get('*/api/v1/rooms/public', () => HttpResponse.json(listPublicExample)),
  http.post('*/api/v1/rooms/quick-match', async ({ request }) => {
    const me = currentPlayer()
    if (!me) return problem(unauthorizedExample, 401)
    const body = await readBody(request)
    const rejected = rejectSource(body)
    if (rejected) return rejected
    const room: RoomResponse = {
      ...toRoom(quickMatchExample),
      roomId: nextId('00000001'),
      players: [{ ...me, isReady: true }],
      playerCount: 1,
      createdAt: new Date().toISOString(),
    }
    openMockRoom(room)
    return HttpResponse.json(room)
  }),
]
