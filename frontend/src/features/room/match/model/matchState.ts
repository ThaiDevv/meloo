import type {
  AudioClip,
  PlayerRankChange,
  PlayerRanking,
  PlayerRoundResult,
  RoomPlayerSummary,
  RoundAnswer,
  RoundScoreDetail,
  ServerRealtimeMessage,
} from '@/shared/realtime/messages'

export type ListenLevel = 2 | 5 | 10 | 20 | 30
export const LISTEN_LEVELS: readonly ListenLevel[] = [2, 5, 10, 20, 30]

export type MatchPhase = 'waiting' | 'prepare' | 'playing' | 'reveal' | 'finished'

export interface Clip {
  url: string
  expiresAt: string
  level: ListenLevel
}

export interface Guessed {
  userId: string
  nickname: string
  level: ListenLevel
  roundScore: number
}

export interface Reveal {
  roundNo: number
  answer: RoundAnswer
  results: PlayerRoundResult[]
  nextRoundAt: string | null
  /** Server clock of the message: `nextRoundAt` minus this is the pause before the next round. */
  serverTime: string
}

export type Notice =
  | { id: number; kind: 'trackReplaced'; roundNo: number }
  | { id: number; kind: 'earlyEnded'; reason: 'ONLY_ONE_PLAYER_REMAINED' | 'HOST_ABANDONED' }

export interface MatchState {
  phase: MatchPhase
  roundNo: number
  totalRounds: 5 | 10 | 15 | null
  /** The newest clip of each round: clips come on a private queue and may beat ROUND_PREPARE. */
  clips: Record<number, Clip>
  timer: { endsAt: string; serverTime: string } | null
  hasGuessed: boolean
  /** No more guesses this round for this player (wrong at 30 s, or gave up). */
  out: boolean
  verdict: { correct: boolean; rightArtist: boolean; nonce: number } | null
  myScore: RoundScoreDetail | null
  /** Who guessed right this round, oldest first. */
  guessed: Guessed[]
  gaveUp: string[]
  scores: Record<string, { totalScore: number; comboStreak: number; rank?: number }>
  /** Everyone seen in the room, so a player who left keeps a card marked "left". */
  roster: Record<string, RoomPlayerSummary>
  reveal: Reveal | null
  notices: Notice[]
  /** Null after a reload: the final table is then rebuilt from `scores`. */
  rankings: PlayerRanking[] | null
  rankChanges: PlayerRankChange[] | null
  /** Counts answers from the server (reply, error), so the screen knows its last action was handled. */
  replies: number
  /** Counts snapshots and ROUND_PREPAREs, so the screen re-confirms `prepared` after either. */
  syncs: number
}

export const INITIAL_MATCH: MatchState = {
  phase: 'waiting',
  roundNo: 0,
  totalRounds: null,
  clips: {},
  timer: null,
  hasGuessed: false,
  out: false,
  verdict: null,
  myScore: null,
  guessed: [],
  gaveUp: [],
  scores: {},
  roster: {},
  reveal: null,
  notices: [],
  rankings: null,
  rankChanges: null,
  replies: 0,
  syncs: 0,
}

export type MatchAction =
  | { type: 'message'; message: ServerRealtimeMessage }
  | { type: 'roster'; players: readonly RoomPlayerSummary[] }
  /** This player pressed "skip": no more guesses this round, without waiting for the echo. */
  | { type: 'gave-up' }

export function listenLevel(seconds: number): ListenLevel {
  return LISTEN_LEVELS.find((level) => level === seconds) ?? 30
}

function clipOf(clip: AudioClip, level?: ListenLevel): Clip {
  return { url: clip.url, expiresAt: clip.expiresAt, level: level ?? listenLevel(clip.level) }
}

function addMs(iso: string, ms: number): string {
  return new Date(Date.parse(iso) + ms).toISOString()
}

function freshRound(): Partial<MatchState> {
  return {
    timer: null,
    hasGuessed: false,
    out: false,
    verdict: null,
    myScore: null,
    guessed: [],
    gaveUp: [],
    reveal: null,
  }
}

function nextNoticeId(state: MatchState): number {
  return (state.notices.at(-1)?.id ?? 0) + 1
}

function scoresOf(
  list: readonly { userId: string; totalScore: number; comboStreak?: number; rank?: number }[],
  previous: MatchState['scores'],
): MatchState['scores'] {
  const next = { ...previous }
  for (const item of list) {
    next[item.userId] = {
      totalScore: item.totalScore,
      comboStreak: item.comboStreak ?? previous[item.userId]?.comboStreak ?? 0,
      // The place is the server's call (BR-10); events that carry no place keep the last one.
      rank: item.rank ?? previous[item.userId]?.rank,
    }
  }
  return next
}

export function rosterOf(players: readonly RoomPlayerSummary[], previous: MatchState['roster']) {
  const next = { ...previous }
  for (const player of players) next[player.userId] = player
  return next
}

/** What the picture of the match becomes after one server message. Pure: no clock, no network. */
export function reduceMessage(state: MatchState, message: ServerRealtimeMessage): MatchState {
  switch (message.type) {
    case 'RESYNC_STATE': {
      const { currentRound } = message
      const sameRound = currentRound !== null && currentRound.roundNo === state.roundNo
      const base: MatchState = {
        ...state,
        // A snapshot replaces whatever the screen was waiting to hear back about.
        replies: state.replies + 1,
        syncs: state.syncs + 1,
        roster: rosterOf(message.room.players, state.roster),
        scores: scoresOf(message.scores, state.scores),
        totalRounds: currentRound?.totalRounds ?? message.room.roundCount,
        roundNo: currentRound?.roundNo ?? message.room.currentRound,
      }
      if (message.roomPhase === 'LOBBY') return { ...base, phase: 'waiting' }
      if (message.roomPhase === 'FINISHED') return { ...base, phase: 'finished' }
      if (message.roomPhase === 'ROUND_REVEAL') {
        const answer = message.lastRoundAnswer
        const results = message.lastRoundResults
        return {
          ...base,
          phase: 'reveal',
          timer: null,
          reveal:
            answer && results
              ? {
                  roundNo: message.room.currentRound,
                  answer,
                  results,
                  nextRoundAt: null,
                  serverTime: message.serverTime,
                }
              : state.reveal,
        }
      }
      if (message.roomPhase === 'ROUND_IN_PROGRESS' && currentRound) {
        return {
          ...base,
          ...(sameRound ? {} : freshRound()),
          phase: 'playing',
          clips: { ...state.clips, [currentRound.roundNo]: clipOf(currentRound.audioClip) },
          timer: {
            serverTime: message.serverTime,
            endsAt: addMs(message.serverTime, currentRound.remainingMs),
          },
          hasGuessed: currentRound.hasGuessed || (sameRound && state.hasGuessed),
          reveal: null,
        }
      }
      // COUNTDOWN or ROUND_PREPARE
      return {
        ...base,
        ...(sameRound ? {} : freshRound()),
        phase: 'prepare',
        clips: currentRound
          ? { ...state.clips, [currentRound.roundNo]: clipOf(currentRound.audioClip) }
          : state.clips,
      }
    }
    case 'ROUND_PREPARE':
      return {
        ...state,
        ...freshRound(),
        replies: state.replies + 1,
        syncs: state.syncs + 1,
        phase: 'prepare',
        roundNo: message.roundNo,
        totalRounds: message.totalRounds,
      }
    case 'ROUND_AUDIO_CLIP':
      return {
        ...state,
        clips: { ...state.clips, [message.roundNo]: clipOf(message.audioClip, message.level) },
      }
    case 'ROUND_START':
      return {
        ...state,
        phase: 'playing',
        roundNo: message.roundNo,
        timer: {
          serverTime: message.serverTime,
          endsAt: addMs(message.serverTime, message.durationMs),
        },
      }
    case 'LISTEN_MORE_RESPONSE':
      return {
        ...state,
        clips: { ...state.clips, [message.roundNo]: clipOf(message.audioClip, message.level) },
        replies: state.replies + 1,
      }
    case 'GUESS_RESULT_PRIVATE': {
      const nonce = (state.verdict?.nonce ?? 0) + 1
      const verdict = {
        correct: message.isCorrect,
        rightArtist: message.isCorrectArtist,
        nonce,
      }
      const next = { ...state, verdict, replies: state.replies + 1 }
      if (message.result === 'CORRECT') {
        return { ...next, hasGuessed: true, myScore: message.scoreDetail }
      }
      if (message.result === 'WRONG_OUT') return { ...next, out: true }
      if (message.nextAudioClip) {
        return {
          ...next,
          clips: {
            ...state.clips,
            [message.roundNo]: clipOf(message.nextAudioClip),
          },
        }
      }
      return next
    }
    case 'PLAYER_GUESSED': {
      const guessed = state.guessed.filter((item) => item.userId !== message.userId)
      guessed.push({
        userId: message.userId,
        nickname: message.nickname,
        level: message.level,
        roundScore: message.roundScore,
      })
      return {
        ...state,
        guessed,
        scores: scoresOf(
          [{ userId: message.userId, totalScore: message.totalScore }],
          state.scores,
        ),
      }
    }
    case 'PLAYER_GAVE_UP':
      return state.gaveUp.includes(message.userId)
        ? state
        : { ...state, gaveUp: [...state.gaveUp, message.userId] }
    case 'ROUND_TRACK_REPLACED':
      return {
        ...state,
        // The round starts over on a new clip: earlier guesses and verdicts no longer count.
        verdict: null,
        notices: [
          ...state.notices,
          { id: nextNoticeId(state), kind: 'trackReplaced', roundNo: message.roundNo },
        ],
      }
    case 'ROUND_END':
      return {
        ...state,
        phase: 'reveal',
        roundNo: message.roundNo,
        timer: null,
        reveal: {
          roundNo: message.roundNo,
          answer: message.answer,
          results: message.results,
          nextRoundAt: message.nextRoundAt,
          serverTime: message.serverTime,
        },
      }
    case 'SCOREBOARD_UPDATE':
      return { ...state, scores: scoresOf(message.scores, state.scores) }
    case 'MATCH_EARLY_ENDED':
      return {
        ...state,
        notices: [
          ...state.notices,
          { id: nextNoticeId(state), kind: 'earlyEnded', reason: message.reason },
        ],
      }
    case 'MATCH_FINISHED':
      return {
        ...state,
        phase: 'finished',
        timer: null,
        rankings: message.rankings,
        scores: scoresOf(message.rankings, state.scores),
      }
    case 'RANK_UPDATED':
      return { ...state, rankChanges: message.changes }
    case 'ERROR':
      return { ...state, replies: state.replies + 1 }
    default:
      return state
  }
}

export function matchReducer(state: MatchState, action: MatchAction): MatchState {
  if (action.type === 'roster') {
    return { ...state, roster: rosterOf(action.players, state.roster) }
  }
  if (action.type === 'gave-up') return { ...state, out: true }
  return reduceMessage(state, action.message)
}
