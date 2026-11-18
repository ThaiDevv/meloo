import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'
import { AdminApiError } from '../../shell/adminApi'

type Schemas = components['schemas']
export type AdminDashboard = Schemas['AdminDashboard']
export type AdminAnalytics = Schemas['AdminAnalytics']
export type AdminAnalyticsDay = Schemas['AdminAnalyticsDay']
export type AdminRatio = Schemas['AdminRatio']

export const ANALYTICS_RANGES = [7, 30, 90] as const
export type AnalyticsRange = (typeof ANALYTICS_RANGES)[number]

/** KPI snapshot: rooms, players, matches today, reports, tracks, users. */
export function useAdminDashboard() {
  return useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/dashboard')
      if (!data) throw new AdminApiError('getAdminDashboard', response.status, error)
      return data
    },
  })
}

/** Daily active/returning players and matches for the last `days` Vietnam days. */
export function useAdminAnalytics(days: AnalyticsRange) {
  return useQuery({
    queryKey: ['admin', 'analytics', days],
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/analytics', {
        params: { query: { days } },
      })
      if (!data) throw new AdminApiError('getAdminAnalytics', response.status, error)
      return data
    },
  })
}
