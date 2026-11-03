import { http, HttpResponse } from 'msw'
import getFiltersExample from '../../../../contracts/examples/catalog/get-filters.200.json'
import listPlaylistsExample from '../../../../contracts/examples/catalog/list-playlists.200.json'

/**
 * Mock handlers for the music picker and catalog:
 * - GET /api/v1/filters: Genres, countries, decades, and artists for filtering tracks.
 * - GET /api/v1/playlists: Playlists available for practice mode, paginated up to size 50.
 */
export const musicPickerHandlers = [
  http.get('*/api/v1/filters', () => {
    return HttpResponse.json(getFiltersExample)
  }),

  http.get('*/api/v1/playlists', ({ request }) => {
    const url = new URL(request.url)
    const pageParam = url.searchParams.get('page')
    const sizeParam = url.searchParams.get('size')

    const page = Math.max(0, parseInt(pageParam ?? '0', 10) || 0)
    const rawSize = parseInt(sizeParam ?? '10', 10) || 10
    // Capped at 50 per AC ("tối đa 50")
    const size = Math.min(50, Math.max(1, rawSize))

    const allItems = listPlaylistsExample.items
    const totalItems = allItems.length
    const totalPages = Math.max(1, Math.ceil(totalItems / size))
    const start = page * size
    const items = allItems.slice(start, start + size)

    return HttpResponse.json({
      page,
      size,
      totalItems,
      totalPages,
      items,
    })
  }),
]
