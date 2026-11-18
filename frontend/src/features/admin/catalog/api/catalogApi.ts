import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'
import { AdminApiError } from '../../shell/adminApi'

type Schemas = components['schemas']
export type AdminTrack = Schemas['AdminTrack']
export type AdminTrackPage = Schemas['AdminTrackPage']
export type AdminImportResult = Schemas['AdminImportResult']
export type AdminTrackImportRequest = Schemas['AdminTrackImportRequest']

export const TRACK_PAGE_SIZE = 20

export interface TrackFilters {
  q?: string
  enabled?: boolean
  genreCode?: string
  countryCode?: string
  decade?: number
  page: number
  size?: number
}

export async function fetchAdminTracks(
  filters: TrackFilters,
  signal?: AbortSignal,
): Promise<AdminTrackPage> {
  const { size = TRACK_PAGE_SIZE, ...rest } = filters
  const { data, error, response } = await apiClient.GET('/api/v1/admin/tracks', {
    params: { query: { ...rest, size } },
    signal,
  })
  if (!data) throw new AdminApiError('listAdminTracks', response.status, error)
  return data
}

/** Search and filter the whole catalog, enabled and disabled tracks alike (BR-27). */
export function useAdminTracks(filters: TrackFilters, enabled = true) {
  return useQuery({
    queryKey: ['admin', 'tracks', filters],
    enabled,
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => fetchAdminTracks(filters, signal),
  })
}

/** Enable or disable one track; a disabled track leaves new rounds and autocomplete (BR-27). */
export function useSetTrackStatus() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (input: { id: string; enabled: boolean; disabledReason?: string | null }) => {
      const { data, error, response } = await apiClient.PUT(
        '/api/v1/admin/tracks/{trackId}/status',
        {
          params: { path: { trackId: input.id } },
          body: {
            enabled: input.enabled,
            disabledReason: input.enabled ? null : (input.disabledReason ?? null),
          },
        },
      )
      if (!data) throw new AdminApiError('setAdminTrackStatus', response.status, error)
      return data
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['admin', 'tracks'] })
      void client.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
      void client.invalidateQueries({ queryKey: ['admin', 'playlists'] })
    },
  })
}

/** Import from the music provider by search text or by one provider track id. */
export function useImportTracks() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (body: AdminTrackImportRequest) => {
      const { data, error, response } = await apiClient.POST('/api/v1/admin/tracks/import', {
        body,
      })
      if (!data) throw new AdminApiError('importAdminTracks', response.status, error)
      return data
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['admin', 'tracks'] })
      void client.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    },
  })
}
