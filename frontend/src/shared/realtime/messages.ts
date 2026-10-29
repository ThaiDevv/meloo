/**
 * Realtime STOMP types generated for Meloo rooms and gameplay (contracts/asyncapi.yaml).
 * Source of truth for client commands and server events over WebSocket.
 */

// --- Shared Payloads ---

export interface AudioClip {
  url: string
  level: 2 | 5 | 10 | 20 | 30
  expiresAt: string
}

export interface RoundAnswer {
  trackId: string
  trackTitle: string
  artistName: string
  albumTitle: string | null
  coverUrl: string | null
  previewFullUrl: string
}

export interface RoundScoreDetail {
  baseScore: number
  comboPercent: number
  speedBonus: number
  roundScore: number
}

export interface PlayerRoundResult {
  userId: string
  outcome: 'CORRECT' | 'WRONG_OUT' | 'TIMEOUT' | 'GAVE_UP'
  correctLevel: number | null
  baseScore: number
  comboPercent: number
  speedBonus: number
  roundScore: number
  wrongCount: number
  answerTimeMs: number | null
}

export interface PlayerScoreSummary {
  userId: string
  nickname: string
  totalScore: number
  comboStreak: number
  rank: number
}

export interface PlayerRanking {
  rank: number
  userId: string
  nickname: string
  totalScore: number
  correctCount: number
  totalCorrectTimeMs: number
}

export interface RoomPlayerSummary {
  userId: string
  nickname: string
  avatarUrl: string | null
  isHost: boolean
  isReady: boolean
  isGuest: boolean
  isConnected: boolean
  score: number
}

export interface RoomResponse {
  roomId: string
  code: string | null
  roomType: 'PRIVATE_ROOM' | 'PUBLIC_ROOM' | 'QUICK_MATCH'
  hostId: string | null
  status: 'LOBBY' | 'PLAYING' | 'FINISHED' | 'CLOSED'
  roundCount: 5 | 10 | 15
  maxPlayers: number
  currentRound: number
  playerCount: number
  players: RoomPlayerSummary[]
  playlistId: string | null
  createdAt: string
}

export interface ActiveRoundState {
  roundNo: number
  totalRounds: 5 | 10 | 15
  currentLevel: 2 | 5 | 10 | 20 | 30
  audioClip: AudioClip
  remainingMs: number
  hasGuessed: boolean
}

// --- Client Commands (/app/rooms/{roomId}/...) ---

export interface ReadyCommand {
  ready: boolean
}

export interface SettingsCommand {
  roundCount?: 5 | 10 | 15
  maxPlayers?: number
  playlistId?: string | null
}

export interface KickCommand {
  targetUserId: string
}

export type LeaveCommand = Record<string, never>
export type StartCommand = Record<string, never>

export interface PreparedCommand {
  roundNo: number
}

export interface ListenMoreCommand {
  roundNo: number
  currentLevel: 2 | 5 | 10 | 20
}

export interface GiveUpCommand {
  roundNo: number
}

export interface GuessCommand {
  roundNo: number
  trackId: string
}

export interface ChatCommand {
  content: string
}

export type ResyncCommand = Record<string, never>

export interface RtcCommand {
  targetUserId?: string | null
  signalType: 'offer' | 'answer' | 'ice-candidate'
  payload: string
}

// --- Server Events (/topic/rooms/{roomId}/...) ---

// Lobby Topic
export interface PlayerJoinedEvent {
  type: 'PLAYER_JOINED'
  user: RoomPlayerSummary
  serverTime: string
}

export interface PlayerLeftEvent {
  type: 'PLAYER_LEFT'
  userId: string
  newHostId: string | null
  reason: 'VOLUNTARY' | 'DISCONNECTED' | 'TIMEOUT'
  serverTime: string
}

export interface PlayerConnectionChangedEvent {
  type: 'PLAYER_CONNECTION_CHANGED'
  userId: string
  isConnected: boolean
  serverTime: string
}

export interface PlayerReadyChangedEvent {
  type: 'PLAYER_READY_CHANGED'
  userId: string
  isReady: boolean
  serverTime: string
}

export interface PlayerKickedEvent {
  type: 'PLAYER_KICKED'
  userId: string
  serverTime: string
}

export interface RoomSettingsChangedEvent {
  type: 'ROOM_SETTINGS_CHANGED'
  roundCount: 5 | 10 | 15
  maxPlayers: number
  playlistId: string | null
  serverTime: string
}

export interface MatchStartingEvent {
  type: 'MATCH_STARTING'
  countdownSeconds: number
  serverTime: string
}

export interface MatchCountdownEvent {
  type: 'MATCH_COUNTDOWN'
  remainingSeconds: number
  serverTime: string
}

export interface MatchCountdownStoppedEvent {
  type: 'MATCH_COUNTDOWN_STOPPED'
  reason: 'PLAYER_LEFT' | 'NOT_ENOUGH_PLAYERS'
  serverTime: string
}

export interface MatchSoloSuggestionEvent {
  type: 'MATCH_SOLO_SUGGESTION'
  message: string
  serverTime: string
}

export interface WaitingListChangedEvent {
  type: 'WAITING_LIST_CHANGED'
  waitingPlayers: RoomPlayerSummary[]
  serverTime: string
}

export interface ReturnToLobbyEvent {
  type: 'RETURN_TO_LOBBY'
  serverTime: string
}

export type LobbyEvent =
  | PlayerJoinedEvent
  | PlayerLeftEvent
  | PlayerConnectionChangedEvent
  | PlayerReadyChangedEvent
  | PlayerKickedEvent
  | RoomSettingsChangedEvent
  | MatchStartingEvent
  | MatchCountdownEvent
  | MatchCountdownStoppedEvent
  | MatchSoloSuggestionEvent
  | WaitingListChangedEvent
  | ReturnToLobbyEvent

// Round Topic
export interface RoundPrepareEvent {
  type: 'ROUND_PREPARE'
  roundNo: number
  totalRounds: 5 | 10 | 15
  timeoutMs: number
  serverTime: string
}

export interface RoundStartEvent {
  type: 'ROUND_START'
  roundNo: number
  durationMs: number
  serverTime: string
}

export interface ListenMoreOpenedEvent {
  type: 'LISTEN_MORE_OPENED'
  userId: string
  level: 2 | 5 | 10 | 20 | 30
  serverTime: string
}

export interface PlayerGaveUpEvent {
  type: 'PLAYER_GAVE_UP'
  userId: string
  roundNo: number
  serverTime: string
}

export interface PlayerGuessedEvent {
  type: 'PLAYER_GUESSED'
  userId: string
  nickname: string
  level: 2 | 5 | 10 | 20 | 30
  isCorrect: true
  serverTime: string
}

export interface RoundTrackReplacedEvent {
  type: 'ROUND_TRACK_REPLACED'
  roundNo: number
  reason: string
  serverTime: string
}

export interface RoundEndEvent {
  type: 'ROUND_END'
  roundNo: number
  answer: RoundAnswer
  results: PlayerRoundResult[]
  nextRoundAt: string | null
  serverTime: string
}

export interface MatchEarlyEndedEvent {
  type: 'MATCH_EARLY_ENDED'
  reason: 'ONLY_ONE_PLAYER_REMAINED' | 'HOST_ABANDONED'
  remainingUserId: string
  serverTime: string
}

export type RoundEvent =
  | RoundPrepareEvent
  | RoundStartEvent
  | ListenMoreOpenedEvent
  | PlayerGaveUpEvent
  | PlayerGuessedEvent
  | RoundTrackReplacedEvent
  | RoundEndEvent
  | MatchEarlyEndedEvent

// Scoreboard Topic
export interface ScoreboardUpdateEvent {
  type: 'SCOREBOARD_UPDATE'
  scores: PlayerScoreSummary[]
  serverTime: string
}

export interface MatchFinishedEvent {
  type: 'MATCH_FINISHED'
  matchId: string
  rankings: PlayerRanking[]
  serverTime: string
}

export type ScoreboardEvent = ScoreboardUpdateEvent | MatchFinishedEvent

// Chat Topic
export interface ChatMessageEvent {
  type: 'CHAT_MESSAGE'
  messageId: string
  senderId: string
  senderNickname: string
  content: string
  sentAt: string
  serverTime: string
}

// RTC Topic
export interface RtcSignalEvent {
  type: 'RTC_SIGNAL'
  senderId: string
  targetUserId: string | null
  signalType: 'offer' | 'answer' | 'ice-candidate'
  payload: string
  serverTime: string
}

// --- Private Queues (/user/queue/...) ---

export interface ErrorMessage {
  type: 'ERROR'
  code:
    | 'ROOM_NOT_FOUND'
    | 'ROOM_FULL'
    | 'NOT_IN_ROOM'
    | 'NOT_HOST'
    | 'NOT_ROOM_HOST'
    | 'ROOM_NOT_LOBBY'
    | 'ROOM_CANNOT_START'
    | 'NOT_ENOUGH_TRACKS'
    | 'INVALID_STATE'
    | 'ALREADY_READY'
    | 'INVALID_ROUND_NO'
    | 'INVALID_LEVEL'
    | 'INVALID_TRACK'
    | 'CHAT_ANSWER_LEAK'
    | 'CHAT_PROHIBITED_WORD'
    | 'RATE_LIMITED'
    | 'INTERNAL_ERROR'
  message: string
  detail: string | null
  startFailureReason?: 'NOT_ENOUGH_PLAYERS' | 'NOT_ALL_READY' | 'NOT_ENOUGH_TRACKS' | null
  missingTracks?: number | null
  serverTime: string
}

export interface ResyncStateMessage {
  type: 'RESYNC_STATE'
  room: RoomResponse
  roomPhase:
    'LOBBY' | 'COUNTDOWN' | 'ROUND_PREPARE' | 'ROUND_IN_PROGRESS' | 'ROUND_REVEAL' | 'FINISHED'
  currentRound: ActiveRoundState | null
  scores: PlayerScoreSummary[]
  lastRoundAnswer: RoundAnswer | null
  lastRoundResults: PlayerRoundResult[] | null
  serverTime: string
}

export interface ListenMoreResponseMessage {
  type: 'LISTEN_MORE_RESPONSE'
  roundNo: number
  level: 2 | 5 | 10 | 20 | 30
  audioClip: AudioClip
  serverTime: string
}

export interface GuessResultMessage {
  type: 'GUESS_RESULT_PRIVATE'
  roundNo: number
  result: 'CORRECT' | 'WRONG_ARTIST' | 'WRONG' | 'WRONG_OUT'
  isCorrect: boolean
  isCorrectArtist: boolean
  scoreDetail: RoundScoreDetail | null
  nextLevel: number | null
  nextAudioClip: AudioClip | null
  serverTime: string
}

export interface RoundAudioClipMessage {
  type: 'ROUND_AUDIO_CLIP'
  roundNo: number
  level: 2 | 5 | 10 | 20 | 30
  audioClip: AudioClip
  serverTime: string
}

export type PrivateQueueMessage =
  | ErrorMessage
  | ResyncStateMessage
  | ListenMoreResponseMessage
  | GuessResultMessage
  | RoundAudioClipMessage

// --- Top-level Union of all Server-to-Client Messages ---

export type ServerRealtimeMessage =
  | LobbyEvent
  | RoundEvent
  | ScoreboardEvent
  | ChatMessageEvent
  | RtcSignalEvent
  | PrivateQueueMessage
