import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'

/** GET /playlists: one page of up to 50 enabled playlists (the standard set and curated sets). */
export function usePlaylists() {
  return useQuery({
    queryKey: ['catalog', 'playlists'],
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/api/v1/playlists', {
        params: { query: { size: 50 } },
      })
      if (!data) throw new Error(`listPlaylists failed: ${String(response.status)}`)
      return data.items
    },
    staleTime: 5 * 60_000,
  })
}
