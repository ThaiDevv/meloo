import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'

type Schemas = components['schemas']

export type RankLeaderboardPage = Schemas['RankLeaderboardPage']
export type RankLeaderboardEntry = Schemas['RankLeaderboardEntry']
export type DailyLeaderboardPage = Schemas['DailyLeaderboardPage']
export type DailyLeaderboardEntry = Schemas['DailyLeaderboardEntry']

export const PAGE_SIZE = 20

export class LeaderboardRequestError extends Error {
  constructor(readonly status: number) {
    super(`leaderboard request failed: ${String(status)}`)
  }
}

/** All-time rank points board (BR-24). Order and `rank` come from the server. */
export function useAllTimeLeaderboard(page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['leaderboard', 'all-time', page],
    enabled,
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/api/v1/leaderboards/all-time', {
        params: { query: { page, size: PAGE_SIZE } },
      })
      if (!data) throw new LeaderboardRequestError(response.status)
      return data
    },
  })
}

/** Today's daily challenge board (BR-25); the server picks today in Asia/Ho_Chi_Minh. */
export function useDailyLeaderboard(page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['leaderboard', 'daily', 'today', page],
    enabled,
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/api/v1/leaderboards/daily', {
        params: { query: { page, size: PAGE_SIZE } },
      })
      if (!data) throw new LeaderboardRequestError(response.status)
      return data
    },
  })
}
