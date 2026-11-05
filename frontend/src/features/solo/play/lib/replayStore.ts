import type { CreateSoloMatchRequest } from '../api/soloMatchApi'

const KEY_PREFIX = 'meloo:solo:replay:'

/**
 * MatchResult does not carry the filter or playlist, so the settings of a match are remembered
 * here while the match is known (in progress). "Play again" on a reopened summary reads them back.
 */
export function rememberReplay(matchId: string, request: CreateSoloMatchRequest): void {
  try {
    localStorage.setItem(KEY_PREFIX + matchId, JSON.stringify(request))
  } catch {
    // Storage may be blocked; Play again then falls back to the setup page.
  }
}

export function recallReplay(matchId: string): CreateSoloMatchRequest | null {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + matchId)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return null
    return parsed as CreateSoloMatchRequest
  } catch {
    return null
  }
}
