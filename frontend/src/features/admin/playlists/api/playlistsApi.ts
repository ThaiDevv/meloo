import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'
import { AdminApiError } from '../../shell/adminApi'

type Schemas = components['schemas']
export type AdminPlaylist = Schemas['AdminPlaylist']

/** Minimum enabled tracks in a playable playlist (BR-09). */
export const MIN_TRACKS = 5

export interface PlaylistDraft {
  code: string
  nameVi: string
  nameEn: string
  enabled: boolean
  trackIds: string[]
}

export const playlistsKey = ['admin', 'playlists'] as const

export function useAdminPlaylists() {
  return useQuery({
    queryKey: playlistsKey,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/playlists')
      if (!data) throw new AdminApiError('listAdminPlaylists', response.status, error)
      return data.items
    },
  })
}

/** What changes between the saved playlist and the draft, so the save makes only needed calls. */
export function planSave(original: AdminPlaylist, draft: PlaylistDraft) {
  const before = new Set(original.trackIds)
  const after = new Set(draft.trackIds)
  return {
    add: draft.trackIds.filter((id) => !before.has(id)),
    remove: original.trackIds.filter((id) => !after.has(id)),
    details:
      original.nameVi !== draft.nameVi ||
      original.nameEn !== draft.nameEn ||
      original.enabled !== draft.enabled,
  }
}

export function useSavePlaylist() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({
      original,
      draft,
    }: {
      original: AdminPlaylist | null
      draft: PlaylistDraft
    }): Promise<AdminPlaylist> => {
      if (!original) {
        const { data, error, response } = await apiClient.POST('/api/v1/admin/playlists', {
          body: {
            code: draft.code,
            nameVi: draft.nameVi,
            nameEn: draft.nameEn,
            trackIds: draft.trackIds,
          },
        })
        if (!data) throw new AdminApiError('createAdminPlaylist', response.status, error)
        return data
      }
      const path = { params: { path: { playlistId: original.id } } }
      const plan = planSave(original, draft)
      let saved = original
      const updateDetails = async () => {
        const { data, error, response } = await apiClient.PUT(
          '/api/v1/admin/playlists/{playlistId}',
          {
            ...path,
            body: { nameVi: draft.nameVi, nameEn: draft.nameEn, enabled: draft.enabled },
          },
        )
        if (!data) throw new AdminApiError('updateAdminPlaylist', response.status, error)
        saved = data
      }
      // Disabling first lets a playlist drop below 5 tracks; enabling last needs the new tracks (BR-09).
      const disabling = original.enabled && !draft.enabled
      if (plan.details && disabling) await updateDetails()
      if (plan.add.length > 0) {
        const { data, error, response } = await apiClient.POST(
          '/api/v1/admin/playlists/{playlistId}/tracks',
          { ...path, body: { trackIds: plan.add } },
        )
        if (!data) throw new AdminApiError('addAdminPlaylistTracks', response.status, error)
        saved = data
      }
      if (plan.remove.length > 0) {
        const { data, error, response } = await apiClient.DELETE(
          '/api/v1/admin/playlists/{playlistId}/tracks',
          { ...path, body: { trackIds: plan.remove } },
        )
        if (!data) throw new AdminApiError('removeAdminPlaylistTracks', response.status, error)
        saved = data
      }
      if (plan.details && !disabling) await updateDetails()
      return saved
    },
    onSettled: () => {
      // A partial save (e.g. tracks added, then 422) must still show what the server holds.
      void client.invalidateQueries({ queryKey: playlistsKey })
      void client.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    },
  })
}
