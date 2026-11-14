import type { components } from '@/shared/api/schema.gen'
import type { RoomPlayerSummary } from '@/shared/realtime/messages'
import type { MatchState } from './matchState'

export interface Standing {
  userId: string
  nickname: string
  avatarUrl: string | null
  avatarId?: components['schemas']['PresetAvatarId'] | null
  score: number
  /** Provisional place in this match; players with the same score share it. */
  rank: number
  /** The place the server last gave this player, when it did. */
  serverRank?: number
  connected: boolean
  left: boolean
  self: boolean
  guessed: { level: number; roundScore: number } | null
  gaveUp: boolean
}

/**
 * The table of the match as it stands: everyone ever seen in the room (a player who left stays, marked
 * as left), best score first. Scores and places come from the server's events; only the order is made here.
 */
export function standingsOf(
  state: MatchState,
  inRoom: readonly RoomPlayerSummary[],
  me: string | null,
): Standing[] {
  const present = new Set(inRoom.map((player) => player.userId))
  const rows = Object.values(state.roster).map((player) => {
    const hit = state.guessed.find((item) => item.userId === player.userId)
    return {
      userId: player.userId,
      nickname: player.nickname,
      avatarUrl: player.avatarUrl,
      avatarId: player.avatarId,
      score: state.scores[player.userId]?.totalScore ?? player.score,
      connected: inRoom.find((p) => p.userId === player.userId)?.isConnected ?? false,
      left: !present.has(player.userId),
      self: player.userId === me,
      guessed: hit ? { level: hit.level, roundScore: hit.roundScore } : null,
      gaveUp: state.gaveUp.includes(player.userId),
    }
  })
  // Equal scores are ordered by the place the server gave (BR-10: who answered right sooner).
  const placeOf = (userId: string) => state.scores[userId]?.rank ?? Number.MAX_SAFE_INTEGER
  rows.sort((a, b) => b.score - a.score || placeOf(a.userId) - placeOf(b.userId))
  return rows.map((row, index) => {
    const tied = rows.filter((other) => other.score === row.score)
    const first = rows.findIndex((other) => other.score === row.score)
    // A tie the server has already broken (BR-10) shows its places; otherwise equal scores share one.
    const places = tied.map((other) => state.scores[other.userId]?.rank)
    const broken =
      tied.length > 1 &&
      places.every((place) => place !== undefined) &&
      new Set(places).size === tied.length
    return {
      ...row,
      rank: (broken ? index : first === -1 ? index : first) + 1,
      serverRank: state.scores[row.userId]?.rank,
    }
  })
}
