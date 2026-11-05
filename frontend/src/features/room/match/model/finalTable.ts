import type { PlayerRanking } from '@/shared/realtime/messages'
import type { MatchState } from './matchState'
import type { Standing } from './standings'

/**
 * The final table: the server's MATCH_FINISHED when it arrived, otherwise (after a reload) rebuilt from
 * the scores in the order of the live table. `left` marks players who are no longer in the room.
 */
export function finalTable(state: MatchState, standings: readonly Standing[]): PlayerRanking[] {
  const gone = new Set(standings.filter((player) => player.left).map((player) => player.userId))
  if (state.rankings) {
    return state.rankings.map((row) => ({ ...row, left: row.left || gone.has(row.userId) }))
  }
  // After a reload the places are the server's; only without them is the order made from the scores.
  const placed = standings.every((player) => player.serverRank !== undefined)
  const ordered = placed
    ? [...standings].sort((a, b) => (a.serverRank ?? 0) - (b.serverRank ?? 0))
    : standings
  return ordered.map((player) => ({
    rank: placed ? (player.serverRank ?? player.rank) : player.rank,
    userId: player.userId,
    nickname: player.nickname,
    totalScore: player.score,
    correctCount: 0,
    totalCorrectTimeMs: 0,
    left: player.left,
  }))
}

/** What the rank section of a quick match shows: waiting, the numbers, or the pointer to the profile. */
export type RankView = 'waiting' | 'ready' | 'timeout'

export const RANK_WAIT_MS = 5_000
