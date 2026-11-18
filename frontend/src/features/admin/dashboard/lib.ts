import type { AdminAnalyticsDay } from './api/dashboardApi'

/** Share of active players that day who had played before; null when nobody played. */
export function returningShare(day: AdminAnalyticsDay): number | null {
  return day.activePlayers === 0 ? null : (day.returningPlayers / day.activePlayers) * 100
}
