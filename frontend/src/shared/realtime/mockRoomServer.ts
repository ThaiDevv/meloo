import { useAuthStore } from '@/shared/api/authStore'
import playerJoinedExample from '../../../../contracts/examples/room/event.lobby.player-joined.json'
import playerKickedExample from '../../../../contracts/examples/room/event.lobby.player-kicked.json'
import playerLeftExample from '../../../../contracts/examples/room/event.lobby.player-left.json'
import readyChangedExample from '../../../../contracts/examples/room/event.lobby.player-ready-changed.json'
import settingsChangedExample from '../../../../contracts/examples/room/event.lobby.settings-changed.json'
import settingsChangedFilterExample from '../../../../contracts/examples/room/event.lobby.settings-changed.filter.json'
import errorExample from '../../../../contracts/examples/room/queue.error.json'
import resyncExample from '../../../../contracts/examples/room/queue.resync-state.json'
import { ERRORS_QUEUE, PRIVATE_QUEUE, topicDestination } from './commands'
import type {
  ErrorMessage,
  ResyncStateMessage,
  RoomPlayerSummary,
  RoomResponse,
  ServerRealtimeMessage,
  SettingsCommand,
} from './messages'
import { closeAllRoomSockets, setStompFactory } from './roomConnection'
import type { StompCallbacks, StompLike } from './stompClient'

/**
 * A stand-in for the room broker, for VITE_API_MOCK=true (the Vercel preview) where MSW cannot
 * fake a WebSocket. It keeps the rooms the mock REST handlers hand out, speaks the lobby part of
 * contracts/asyncapi.yaml (snapshot, ready, settings, kick, leave) and builds every message from
 * contracts/examples/room/**. Starting a match and everything after it are not mocked.
 */

type Listener = (message: { body: string }) => void

interface Sub {
  destination: string
  callback: Listener
}

interface MockRoom {
  room: RoomResponse
  /** Users the host removed (BR-12): the REST join refuses them. */
  kicked: Set<string>
  /** A second player joins once the creator is connected, so the list has more than one row. */
  companionPending: boolean
}

export interface MockRealtimeOptions {
  /** Delay before a connection is accepted. */
  connectDelayMs?: number
  /** When the pretend second player joins a room after its creator connects; null: never. */
  companionDelayMs?: number | null
}

const rooms = new Map<string, MockRoom>()
const clients = new Set<MockStomp>()
const DEFAULT_OPTIONS: Required<MockRealtimeOptions> = {
  connectDelayMs: 150,
  companionDelayMs: 2_000,
}
let options: Required<MockRealtimeOptions> = DEFAULT_OPTIONS

/** The pretend second player: PlayerTwo of contracts/examples/room/join-room.200.json. */
const COMPANION: RoomPlayerSummary = {
  userId: playerJoinedExample.user.userId,
  nickname: playerJoinedExample.user.nickname,
  avatarUrl: playerJoinedExample.user.avatarUrl,
  isHost: false,
  isReady: false,
  isGuest: playerJoinedExample.user.isGuest,
  isConnected: true,
  score: 0,
}

function now(): string {
  return new Date().toISOString()
}

/** Registers a room the REST mock created or let someone join. */
export function openMockRoom(room: RoomResponse, companionPending = false): void {
  const known = rooms.get(room.roomId)
  rooms.set(room.roomId, {
    room,
    kicked: known?.kicked ?? new Set(),
    companionPending: known?.companionPending ?? companionPending,
  })
}

export function findMockRoomByCode(code: string): RoomResponse | null {
  for (const { room } of rooms.values()) if (room.code === code) return room
  return null
}

export function hasBeenKicked(roomId: string, userId: string): boolean {
  return rooms.get(roomId)?.kicked.has(userId) ?? false
}

/** Adds a player to a registered room (no broadcast: it is the REST join that does this). */
export function addMockPlayer(roomId: string, player: RoomPlayerSummary): RoomResponse | null {
  const entry = rooms.get(roomId)
  if (!entry) return null
  const players = entry.room.players.some((p) => p.userId === player.userId)
    ? entry.room.players
    : [...entry.room.players, player]
  entry.room = { ...entry.room, players, playerCount: players.length }
  return entry.room
}

/** For tests: forgets every room and connection and restores the default timings. */
export function resetMockRealtime(): void {
  rooms.clear()
  clients.clear()
  options = DEFAULT_OPTIONS
}

function roomOf(roomId: string): MockRoom | null {
  return rooms.get(roomId) ?? null
}

function withPlayers(entry: MockRoom, players: RoomPlayerSummary[]): void {
  entry.room = { ...entry.room, players, playerCount: players.length }
}

/** Sends `message` to every connection subscribed to the lobby topic of `roomId`. */
function broadcastLobby(roomId: string, message: ServerRealtimeMessage): void {
  const destination = topicDestination(roomId, 'lobby')
  for (const client of clients) client.deliver(destination, message)
}

function errorMessage(code: ErrorMessage['code'], extra: Partial<ErrorMessage> = {}): ErrorMessage {
  return { ...errorExample, type: 'ERROR', code, detail: null, serverTime: now(), ...extra }
}

function snapshot(entry: MockRoom): ResyncStateMessage {
  return {
    ...resyncExample,
    type: 'RESYNC_STATE',
    room: entry.room,
    roomPhase: 'LOBBY',
    currentRound: null,
    scores: [],
    lastRoundAnswer: null,
    lastRoundResults: null,
    serverTime: now(),
  }
}

/** The pretend player walks in, then marks itself ready a moment later. */
export function joinMockCompanion(roomId: string): void {
  const entry = roomOf(roomId)
  if (!entry || entry.room.players.some((p) => p.userId === COMPANION.userId)) return
  entry.companionPending = false
  withPlayers(entry, [...entry.room.players, COMPANION])
  broadcastLobby(roomId, {
    ...playerJoinedExample,
    type: 'PLAYER_JOINED',
    user: COMPANION,
    serverTime: now(),
  })
}

export function readyMockCompanion(roomId: string): void {
  setReady(roomId, COMPANION.userId, true)
}

function setReady(roomId: string, userId: string, isReady: boolean): void {
  const entry = roomOf(roomId)
  if (!entry) return
  withPlayers(
    entry,
    entry.room.players.map((p) => (p.userId === userId ? { ...p, isReady } : p)),
  )
  broadcastLobby(roomId, {
    ...readyChangedExample,
    type: 'PLAYER_READY_CHANGED',
    userId,
    isReady,
    serverTime: now(),
  })
}

function applySettings(entry: MockRoom, body: SettingsCommand): void {
  const { room } = entry
  const filter = body.filter ?? null
  const next: RoomResponse = {
    ...room,
    roundCount: body.roundCount ?? room.roundCount,
    maxPlayers: body.maxPlayers ?? room.maxPlayers,
    playlistId: filter ? null : (body.playlistId ?? room.playlistId),
    filter: filter ?? (body.playlistId ? null : room.filter),
  }
  entry.room = next
  const base = next.filter ? settingsChangedFilterExample : settingsChangedExample
  broadcastLobby(room.roomId, {
    ...base,
    type: 'ROOM_SETTINGS_CHANGED',
    roundCount: next.roundCount === 5 || next.roundCount === 15 ? next.roundCount : 10,
    maxPlayers: next.maxPlayers,
    playlistId: next.playlistId,
    filter: next.filter,
    serverTime: now(),
  })
}

function removePlayer(entry: MockRoom, userId: string): string | null {
  const rest = entry.room.players.filter((p) => p.userId !== userId)
  const wasHost = entry.room.hostId === userId
  const heir = wasHost ? (rest[0]?.userId ?? null) : null
  withPlayers(
    entry,
    rest.map((p) => (p.userId === heir ? { ...p, isHost: true } : p)),
  )
  if (wasHost) entry.room = { ...entry.room, hostId: heir }
  return heir
}

/** What the pretend server does with one client command. Returns what to send back to `client`. */
function handleCommand(client: MockStomp, roomId: string, command: string, body: unknown): void {
  const entry = roomOf(roomId)
  const me = client.userId
  if (!entry || !entry.room.players.some((p) => p.userId === me)) {
    client.deliver(ERRORS_QUEUE, errorMessage('NOT_IN_ROOM'))
    return
  }
  const isHost = entry.room.hostId === me
  switch (command) {
    case 'resync':
      client.deliver(PRIVATE_QUEUE, snapshot(entry))
      if (entry.companionPending && options.companionDelayMs !== null) {
        entry.companionPending = false
        setTimeout(() => {
          joinMockCompanion(roomId)
          setTimeout(() => {
            readyMockCompanion(roomId)
          }, options.companionDelayMs ?? 0)
        }, options.companionDelayMs)
      }
      return
    case 'ready': {
      const ready = typeof body === 'object' && body !== null && 'ready' in body && body.ready
      setReady(roomId, me, ready === true)
      return
    }
    case 'settings':
      if (!isHost) client.deliver(ERRORS_QUEUE, errorMessage('NOT_HOST'))
      else applySettings(entry, settingsOf(body))
      return
    case 'kick': {
      const target =
        typeof body === 'object' && body !== null && 'targetUserId' in body
          ? body.targetUserId
          : null
      if (!isHost) client.deliver(ERRORS_QUEUE, errorMessage('NOT_HOST'))
      else if (typeof target === 'string' && target !== me) {
        broadcastLobby(roomId, {
          ...playerKickedExample,
          type: 'PLAYER_KICKED',
          userId: target,
          serverTime: now(),
        })
        entry.kicked.add(target)
        removePlayer(entry, target)
      }
      return
    }
    case 'leave': {
      const newHostId = removePlayer(entry, me)
      broadcastLobby(roomId, {
        ...playerLeftExample,
        type: 'PLAYER_LEFT',
        userId: me,
        newHostId,
        reason: 'VOLUNTARY',
        serverTime: now(),
      })
      return
    }
    case 'start': {
      const players = entry.room.players
      const startFailureReason =
        players.length < 2
          ? 'NOT_ENOUGH_PLAYERS'
          : players.some((p) => !p.isReady)
            ? 'NOT_ALL_READY'
            : null
      client.deliver(
        ERRORS_QUEUE,
        errorMessage('ROOM_CANNOT_START', {
          message: 'Mock: chưa hỗ trợ bắt đầu trận trên bản preview.',
          startFailureReason,
        }),
      )
      return
    }
    default:
  }
}

function settingsOf(body: unknown): SettingsCommand {
  if (typeof body !== 'object' || body === null) return {}
  const settings: SettingsCommand = {}
  if (
    'roundCount' in body &&
    (body.roundCount === 5 || body.roundCount === 10 || body.roundCount === 15)
  )
    settings.roundCount = body.roundCount
  if ('maxPlayers' in body && typeof body.maxPlayers === 'number')
    settings.maxPlayers = body.maxPlayers
  if ('playlistId' in body && (typeof body.playlistId === 'string' || body.playlistId === null))
    settings.playlistId = body.playlistId
  if ('filter' in body && typeof body.filter === 'object')
    settings.filter = body.filter === null ? null : { ...body.filter }
  return settings
}

class MockStomp implements StompLike {
  connected = false
  private readonly subs = new Set<Sub>()
  private readonly callbacks: StompCallbacks
  private timer: ReturnType<typeof setTimeout> | null = null

  constructor(callbacks: StompCallbacks) {
    this.callbacks = callbacks
  }

  /** The signed-in mock user, whose id the broker would read from the bearer token. */
  get userId(): string {
    return useAuthStore.getState().user?.id ?? ''
  }

  activate(): void {
    clients.add(this)
    this.timer = setTimeout(() => {
      this.connected = true
      this.callbacks.onConnect()
    }, options.connectDelayMs)
  }

  deactivate(): Promise<void> {
    if (this.timer) clearTimeout(this.timer)
    this.connected = false
    this.subs.clear()
    clients.delete(this)
    return Promise.resolve()
  }

  subscribe(destination: string, callback: Listener) {
    const sub: Sub = { destination, callback }
    this.subs.add(sub)
    return {
      unsubscribe: () => {
        this.subs.delete(sub)
      },
    }
  }

  publish(params: { destination: string; body: string }): void {
    const roomId = [...rooms.keys()].find((id) =>
      params.destination.startsWith(`/app/rooms/${id}/`),
    )
    if (!roomId) return
    const command = params.destination.slice(`/app/rooms/${roomId}/`.length)
    let body: unknown
    try {
      body = JSON.parse(params.body)
    } catch {
      return
    }
    handleCommand(this, roomId, command, body)
  }

  /** Delivers `message` to this connection when it listens on `destination`. */
  deliver(destination: string, message: ServerRealtimeMessage): void {
    for (const sub of [...this.subs]) {
      if (sub.destination === destination) sub.callback({ body: JSON.stringify(message) })
    }
  }
}

/** Swaps the real STOMP client for the pretend broker. Returns what undoes it. */
export function installMockRealtime(next: MockRealtimeOptions = {}): () => void {
  options = { ...DEFAULT_OPTIONS, ...next }
  setStompFactory((callbacks) => new MockStomp(callbacks))
  return () => {
    closeAllRoomSockets()
    setStompFactory()
  }
}
