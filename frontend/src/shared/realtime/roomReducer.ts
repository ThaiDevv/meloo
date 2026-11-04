import type {
  ErrorMessage,
  MatchCountdownStoppedEvent,
  ResyncStateMessage,
  RoomPlayerSummary,
  RoomResponse,
  ServerRealtimeMessage,
} from './messages'

export type RoomPhase = ResyncStateMessage['roomPhase']

/** How far the client is from a trustworthy picture of the room. */
export type RoomLink = 'idle' | 'connecting' | 'syncing' | 'live' | 'reconnecting'

/** The part of the room store that server messages change. */
export interface RoomData {
  room: RoomResponse | null
  phase: RoomPhase
  /** Seconds left before the match starts (host-started or matchmaking countdown); null when none. */
  countdown: number | null
  countdownStoppedReason: MatchCountdownStoppedEvent['reason'] | null
  /** BR-13: "alone for 60 s" hint; null when there is none. */
  soloSuggestion: string | null
  /** BR-15: players who joined while a match runs. */
  waitingPlayers: RoomPlayerSummary[]
  /** True once MATCH_STARTING came and no RETURN_TO_LOBBY / stop followed: this client is in the match. */
  matchStarting: boolean
  /** Counts ROUND_PREPARE events that arrived live, so a screen can react to "the match began". */
  roundPrepareCount: number
  kickedUserIds: string[]
  lastError: ErrorMessage | null
}

export const EMPTY_ROOM_DATA: RoomData = {
  room: null,
  phase: 'LOBBY',
  countdown: null,
  countdownStoppedReason: null,
  soloSuggestion: null,
  waitingPlayers: [],
  matchStarting: false,
  roundPrepareCount: 0,
  kickedUserIds: [],
  lastError: null,
}

function withPlayers(room: RoomResponse, players: RoomPlayerSummary[]): RoomResponse {
  return { ...room, players, playerCount: players.length }
}

function mapPlayer(
  room: RoomResponse,
  userId: string,
  change: (player: RoomPlayerSummary) => RoomPlayerSummary,
): RoomResponse {
  return {
    ...room,
    players: room.players.map((player) => (player.userId === userId ? change(player) : player)),
  }
}

/**
 * Applies one server message to the room picture. Pure: returns the changed fields only, or null
 * when the message does not concern the room (chat, voice, scoreboard...) or there is no room yet.
 */
export function reduceRoomMessage(
  data: RoomData,
  message: ServerRealtimeMessage,
): Partial<RoomData> | null {
  const { room } = data
  switch (message.type) {
    case 'RESYNC_STATE':
      return {
        room: message.room,
        phase: message.roomPhase,
        // A snapshot is the whole truth: whatever transient countdown we held may be stale.
        countdown: message.roomPhase === 'LOBBY' ? null : data.countdown,
        matchStarting: message.roomPhase !== 'LOBBY' ? data.matchStarting : false,
      }
    case 'ERROR':
      return { lastError: message }
    case 'WAITING_LIST_CHANGED':
      return { waitingPlayers: message.waitingPlayers }
    case 'MATCH_STARTING':
      return {
        phase: 'COUNTDOWN',
        countdown: message.countdownSeconds,
        countdownStoppedReason: null,
        soloSuggestion: null,
        matchStarting: true,
      }
    case 'MATCH_COUNTDOWN':
      return { countdown: message.remainingSeconds, countdownStoppedReason: null }
    case 'MATCH_COUNTDOWN_STOPPED':
      return {
        phase: 'LOBBY',
        countdown: null,
        countdownStoppedReason: message.reason,
        matchStarting: false,
      }
    case 'MATCH_SOLO_SUGGESTION':
      return { soloSuggestion: message.message }
    case 'RETURN_TO_LOBBY':
      return {
        phase: 'LOBBY',
        countdown: null,
        countdownStoppedReason: null,
        matchStarting: false,
        waitingPlayers: [],
        room: room ? { ...room, status: 'LOBBY', currentRound: 0 } : room,
      }
    case 'ROUND_PREPARE':
      return {
        phase: 'ROUND_PREPARE',
        countdown: null,
        roundPrepareCount: data.roundPrepareCount + 1,
        room: room ? { ...room, status: 'PLAYING', currentRound: message.roundNo } : room,
      }
    case 'ROUND_START':
      return { phase: 'ROUND_IN_PROGRESS' }
    case 'ROUND_END':
      return { phase: 'ROUND_REVEAL' }
    case 'MATCH_FINISHED':
      return { phase: 'FINISHED' }
    default:
      break
  }

  // Everything below edits the room itself, so it needs one.
  if (!room) return null
  switch (message.type) {
    case 'PLAYER_JOINED': {
      const others = room.players.filter((player) => player.userId !== message.user.userId)
      return { room: withPlayers(room, [...others, message.user]) }
    }
    case 'PLAYER_LEFT': {
      // A room without a host (quick match) stays that way instead of promoting someone.
      const hostId = room.hostId === null ? null : message.newHostId
      const players = room.players
        .filter((player) => player.userId !== message.userId)
        .map((player) => ({ ...player, isHost: hostId !== null && player.userId === hostId }))
      return { room: { ...withPlayers(room, players), hostId } }
    }
    case 'PLAYER_CONNECTION_CHANGED':
      return {
        room: mapPlayer(room, message.userId, (player) => ({
          ...player,
          isConnected: message.isConnected,
        })),
      }
    case 'PLAYER_READY_CHANGED':
      return {
        room: mapPlayer(room, message.userId, (player) => ({
          ...player,
          isReady: message.isReady,
        })),
      }
    case 'PLAYER_KICKED':
      return {
        room: withPlayers(
          room,
          room.players.filter((player) => player.userId !== message.userId),
        ),
        kickedUserIds: [...data.kickedUserIds, message.userId],
      }
    case 'ROOM_SETTINGS_CHANGED':
      return {
        room: {
          ...room,
          roundCount: message.roundCount,
          maxPlayers: message.maxPlayers,
          playlistId: message.playlistId,
          filter: message.filter ?? null,
        },
      }
    default:
      return null
  }
}
