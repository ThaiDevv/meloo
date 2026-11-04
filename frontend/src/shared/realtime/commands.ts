import type {
  ChatCommand,
  GiveUpCommand,
  GuessCommand,
  KickCommand,
  LeaveCommand,
  ListenMoreCommand,
  PreparedCommand,
  ReadyCommand,
  ResyncCommand,
  RtcCommand,
  SettingsCommand,
  StartCommand,
} from './messages'

/** Client commands, keyed by the last path segment of their destination. */
export interface RoomCommands {
  ready: ReadyCommand
  settings: SettingsCommand
  kick: KickCommand
  leave: LeaveCommand
  start: StartCommand
  prepared: PreparedCommand
  'listen-more': ListenMoreCommand
  'give-up': GiveUpCommand
  guess: GuessCommand
  chat: ChatCommand
  resync: ResyncCommand
  rtc: RtcCommand
}

/** The broadcast topics of a room. */
export type RoomTopic = 'lobby' | 'round' | 'scoreboard' | 'chat' | 'rtc'

// One literal per destination of contracts/asyncapi.yaml, so a typo fails to compile and
// .claude/skills/meloo-qa-check/scripts/stomp-destinations.py can match each one with the contract.
const COMMAND_DESTINATIONS: Record<keyof RoomCommands, (roomId: string) => string> = {
  ready: (id) => `/app/rooms/${id}/ready`,
  settings: (id) => `/app/rooms/${id}/settings`,
  kick: (id) => `/app/rooms/${id}/kick`,
  leave: (id) => `/app/rooms/${id}/leave`,
  start: (id) => `/app/rooms/${id}/start`,
  prepared: (id) => `/app/rooms/${id}/prepared`,
  'listen-more': (id) => `/app/rooms/${id}/listen-more`,
  'give-up': (id) => `/app/rooms/${id}/give-up`,
  guess: (id) => `/app/rooms/${id}/guess`,
  chat: (id) => `/app/rooms/${id}/chat`,
  resync: (id) => `/app/rooms/${id}/resync`,
  rtc: (id) => `/app/rooms/${id}/rtc`,
}

const TOPIC_DESTINATIONS: Record<RoomTopic, (roomId: string) => string> = {
  lobby: (id) => `/topic/rooms/${id}/lobby`,
  round: (id) => `/topic/rooms/${id}/round`,
  scoreboard: (id) => `/topic/rooms/${id}/scoreboard`,
  chat: (id) => `/topic/rooms/${id}/chat`,
  rtc: (id) => `/topic/rooms/${id}/rtc`,
}

export function commandDestination(roomId: string, command: keyof RoomCommands): string {
  return COMMAND_DESTINATIONS[command](roomId)
}

export function topicDestination(roomId: string, topic: RoomTopic): string {
  return TOPIC_DESTINATIONS[topic](roomId)
}

/** Private queues: errors of the user's own commands, and everything meant for one player. */
export const ERRORS_QUEUE = '/user/queue/errors'
export const PRIVATE_QUEUE = '/user/queue/private'
