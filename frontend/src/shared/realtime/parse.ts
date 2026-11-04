import type { ServerRealtimeMessage } from './messages'

/** Every `type` the contract (contracts/asyncapi.yaml) can put on a topic or a private queue. */
const KNOWN_TYPES: ReadonlySet<string> = new Set<ServerRealtimeMessage['type']>([
  'PLAYER_JOINED',
  'PLAYER_LEFT',
  'PLAYER_CONNECTION_CHANGED',
  'PLAYER_READY_CHANGED',
  'PLAYER_KICKED',
  'ROOM_SETTINGS_CHANGED',
  'MATCH_STARTING',
  'MATCH_COUNTDOWN',
  'MATCH_COUNTDOWN_STOPPED',
  'MATCH_SOLO_SUGGESTION',
  'WAITING_LIST_CHANGED',
  'RETURN_TO_LOBBY',
  'ROUND_PREPARE',
  'ROUND_START',
  'LISTEN_MORE_OPENED',
  'PLAYER_GAVE_UP',
  'PLAYER_GUESSED',
  'ROUND_TRACK_REPLACED',
  'ROUND_END',
  'MATCH_EARLY_ENDED',
  'SCOREBOARD_UPDATE',
  'MATCH_FINISHED',
  'CHAT_MESSAGE',
  'RTC_SIGNAL',
  'ERROR',
  'RESYNC_STATE',
  'LISTEN_MORE_RESPONSE',
  'GUESS_RESULT_PRIVATE',
  'ROUND_AUDIO_CLIP',
])

/**
 * Shallow check: an object whose `type` the contract knows. The payload shape is the server's
 * promise (messages.ts mirrors the contract), so it is not re-validated field by field here.
 */
export function isServerMessage(value: unknown): value is ServerRealtimeMessage {
  return (
    typeof value === 'object' &&
    value !== null &&
    'type' in value &&
    typeof value.type === 'string' &&
    KNOWN_TYPES.has(value.type)
  )
}

/** Parses one STOMP frame body. Anything that is not a known message is dropped (null). */
export function parseServerMessage(body: string): ServerRealtimeMessage | null {
  let value: unknown
  try {
    value = JSON.parse(body)
  } catch {
    return null
  }
  return isServerMessage(value) ? value : null
}
