import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'

/** GET /filters: the genres, countries, decades and artists that have at least one enabled track. */
export function useCatalogFilters() {
  return useQuery({
    queryKey: ['catalog', 'filters'],
    queryFn: async () => {
      const { data, response } = await apiClient.GET('/api/v1/filters')
      if (!data) throw new Error(`getFilters failed: ${String(response.status)}`)
      return data
    },
    staleTime: 5 * 60_000,
  })
}
