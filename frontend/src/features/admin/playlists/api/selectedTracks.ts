import { useQuery } from '@tanstack/react-query'
import { fetchAdminTracks, type AdminTrack } from '../../catalog/api/catalogApi'

/** Resolve existing selections, including disabled songs, using the paged admin catalog API. */
export function useSelectedTracks(trackIds: string[]) {
  return useQuery({
    queryKey: ['admin', 'tracks', 'selected', trackIds],
    enabled: trackIds.length > 0,
    queryFn: async ({ signal }) => {
      const wanted = new Set(trackIds)
      const found = new Map<string, AdminTrack>()
      let totalPages = 1
      for (let page = 0; page < totalPages && found.size < wanted.size; page++) {
        const batch = await fetchAdminTracks({ page, size: 50 }, signal)
        totalPages = batch.totalPages
        for (const track of batch.items) {
          if (wanted.has(track.id)) found.set(track.id, track)
        }
      }
      return found
    },
  })
}
