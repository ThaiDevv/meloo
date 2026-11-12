import type { RoundResult, RoundState } from '@/features/game-core'
import type { DailyResult, DailyRoundState } from '../api/dailyApi'

/**
 * game-core draws a `RoundState`; the daily attempt is one round with the 5-minute ceiling on the
 * side. There is no match, so the date stands in for the id.
 */
export function toRoundState(round: DailyRoundState): RoundState {
  return {
    matchId: round.challengeDate,
    roundNo: 1,
    roundCount: 5,
    level: round.level,
    listenSeconds: round.listenSeconds,
    canListenMore: round.canListenMore,
    canSkip: round.canSkip,
    audio: round.audio,
    serverTime: round.serverTime,
  }
}

/** The reveal of game-core takes a `RoundResult`; the daily has no combo and no speed bonus (BR-08, BR-25). */
export function toRoundResult(result: DailyResult): RoundResult {
  return {
    roundNo: 1,
    outcome: result.outcome,
    ...(result.correctLevel != null && { correctLevel: result.correctLevel }),
    baseScore: result.score,
    comboPercent: 0,
    speedBonus: 0,
    roundScore: result.score,
    wrongCount: result.wrongCount,
    comboStreak: 0,
    totalScore: result.score,
    answer: result.answer,
  }
}
