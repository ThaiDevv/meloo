import type { GuessVerdict, RoundResult } from '@/features/game-core'
import type {
  GuessResponse,
  MatchResult,
  RoundEnd,
  RoundState,
  SoloMatch,
} from '../api/soloMatchApi'

/**
 * The screen's copy of a match in progress. Every number in it is copied from a server response:
 * the client never adds up a score or decides an answer (NFR-07, BR-02, BR-08).
 */
export interface PlayState {
  round: RoundState
  totalScore: number
  comboStreak: number
  verdict: GuessVerdict | null
  /** The finished round's result; the answer is only ever read from here. */
  result: RoundResult | null
  /** Already opened by the server when the round ended (RoundEnd.nextRound). */
  nextRound: RoundState | null
  matchFinished: boolean
}

export type PlayAction =
  | { type: 'guessed'; response: GuessResponse; nonce: number }
  | { type: 'level-opened'; round: RoundState }
  | { type: 'round-ended'; end: RoundEnd }
  | { type: 'resynced'; round: RoundState }
  | { type: 'continued' }

export function initialPlayState(match: SoloMatch): PlayState {
  return {
    round: match.currentRound,
    totalScore: match.totalScore,
    comboStreak: match.comboStreak,
    verdict: null,
    result: null,
    nextRound: null,
    matchFinished: false,
  }
}

function endRound(state: PlayState, end: RoundEnd): PlayState {
  return {
    ...state,
    result: end.result,
    totalScore: end.result.totalScore,
    comboStreak: end.result.comboStreak,
    nextRound: end.nextRound ?? null,
    matchFinished: end.matchFinished,
  }
}

export function playReducer(state: PlayState, action: PlayAction): PlayState {
  switch (action.type) {
    case 'guessed': {
      const { response, nonce } = action
      const verdict = { correct: response.correct, rightArtist: response.rightArtist, nonce }
      if (response.roundEnded && response.end) return { ...endRound(state, response.end), verdict }
      return { ...state, round: response.round ?? state.round, verdict }
    }
    case 'level-opened':
      return { ...state, round: action.round }
    case 'round-ended':
      return endRound(state, action.end)
    case 'resynced':
      return { ...state, round: action.round, verdict: null, result: null, nextRound: null }
    case 'continued':
      if (!state.nextRound) return state
      return { ...state, round: state.nextRound, verdict: null, result: null, nextRound: null }
  }
}

/** BR-08 as the server counted it: the longest run of `comboStreak` across the finished rounds. */
export function longestCombo(result: MatchResult): number {
  return result.rounds.reduce((best, round) => Math.max(best, round.comboStreak), 0)
}
