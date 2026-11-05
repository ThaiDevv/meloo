import type { RankUpdatedEvent } from '@/shared/realtime/messages'

/**
 * Reads a RANK_UPDATED frame (BR-13, BR-24). The shared parser (shared/realtime/parse.ts) does not know
 * this type yet and drops it, so the match screen reads it from the scoreboard topic itself.
 */
export function parseRankUpdated(body: string): RankUpdatedEvent | null {
  let value: unknown
  try {
    value = JSON.parse(body)
  } catch {
    return null
  }
  if (typeof value !== 'object' || value === null) return null
  if (!('type' in value) || value.type !== 'RANK_UPDATED') return null
  if (!('changes' in value) || !Array.isArray(value.changes)) return null
  const changes = value.changes.filter(
    (item: unknown): item is RankUpdatedEvent['changes'][number] =>
      typeof item === 'object' &&
      item !== null &&
      'userId' in item &&
      typeof item.userId === 'string' &&
      'rankDelta' in item &&
      typeof item.rankDelta === 'number' &&
      'rankPoints' in item &&
      typeof item.rankPoints === 'number',
  )
  return {
    type: 'RANK_UPDATED',
    matchId: 'matchId' in value && typeof value.matchId === 'string' ? value.matchId : '',
    changes,
    serverTime:
      'serverTime' in value && typeof value.serverTime === 'string' ? value.serverTime : '',
  }
}
