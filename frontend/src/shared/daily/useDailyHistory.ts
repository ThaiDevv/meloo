import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'

export type DailyHistory = components['schemas']['DailyHistoryResponse']

export const DAILY_HISTORY_KEY = ['daily', 'history'] as const

/**
 * The player's solved days and streaks (`GET /daily/history`). The server counts the streaks and names
 * "today"; the home calendar and the daily page only show them (BR-25). Pass `enabled: false` while
 * there is no session.
 */
export function useDailyHistory(enabled = true) {
  return useQuery({
    queryKey: DAILY_HISTORY_KEY,
    enabled,
    staleTime: 60_000,
    queryFn: async (): Promise<DailyHistory> => {
      const { data, response } = await apiClient.GET('/api/v1/daily/history')
      if (!data) throw new Error(`getDailyHistory failed: ${String(response.status)}`)
      return data
    },
  })
}
