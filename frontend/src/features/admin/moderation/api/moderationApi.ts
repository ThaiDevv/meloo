import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'
import { AdminApiError } from '../../shell/adminApi'

type Schema = components['schemas']
export type Report = Schema['AdminChatReport']
export type Word = Schema['AdminBannedWord']
export type Schedule = Schema['AdminDailyScheduleItem']
export function useReports(status: Schema['ChatReportStatus'], page: number) {
  return useQuery({
    queryKey: ['admin', 'reports', status, page],
    queryFn: async ({ signal }) => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/reports', {
        params: { query: { status, page, size: 20 } },
        signal,
      })
      if (!data) throw new AdminApiError('listAdminChatReports', response.status, error)
      return data
    },
  })
}
export function useWords() {
  return useQuery({
    queryKey: ['admin', 'words'],
    queryFn: async ({ signal }) => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/banned-words', {
        signal,
      })
      if (!data) throw new AdminApiError('listAdminBannedWords', response.status, error)
      return data
    },
  })
}
export function useSchedule(range: { from?: string; to?: string }) {
  return useQuery({
    queryKey: ['admin', 'schedule', range],
    queryFn: async ({ signal }) => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/daily-schedule', {
        params: { query: range },
        signal,
      })
      if (!data) throw new AdminApiError('listAdminDailySchedule', response.status, error)
      return data
    },
  })
}
/** One mutation per editor; all writes refresh the immutable audit view. */
export function useModerationChange() {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: async (
      change:
        | { type: 'resolve'; id: string; body: Schema['AdminResolveChatReportRequest'] }
        | { type: 'word'; id?: string; word: string }
        | { type: 'deleteWord'; id: string }
        | { type: 'schedule'; date: string; trackId: string },
    ) => {
      switch (change.type) {
        case 'resolve': {
          const { data, error, response } = await apiClient.POST(
            '/api/v1/admin/reports/{reportId}/resolve',
            { params: { path: { reportId: change.id } }, body: change.body },
          )
          if (!data) throw new AdminApiError('resolveAdminChatReport', response.status, error)
          break
        }
        case 'word': {
          const body = { word: change.word }
          const { data, error, response } = change.id
            ? await apiClient.PUT('/api/v1/admin/banned-words/{wordId}', {
                params: { path: { wordId: change.id } },
                body,
              })
            : await apiClient.POST('/api/v1/admin/banned-words', { body })
          if (!data) throw new AdminApiError('saveAdminBannedWord', response.status, error)
          break
        }
        case 'deleteWord': {
          const { error, response } = await apiClient.DELETE(
            '/api/v1/admin/banned-words/{wordId}',
            { params: { path: { wordId: change.id } } },
          )
          if (!response.ok) throw new AdminApiError('deleteAdminBannedWord', response.status, error)
          break
        }
        case 'schedule': {
          const { data, error, response } = await apiClient.PUT(
            '/api/v1/admin/daily-schedule/{challengeDate}',
            { params: { path: { challengeDate: change.date } }, body: { trackId: change.trackId } },
          )
          if (!data) throw new AdminApiError('scheduleAdminDailyChallenge', response.status, error)
        }
      }
    },
    onSuccess: () => {
      for (const key of ['reports', 'words', 'schedule', 'users', 'user', 'audit', 'dashboard'])
        void cache.invalidateQueries({ queryKey: ['admin', key] })
    },
  })
}
