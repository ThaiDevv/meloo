import { http, HttpResponse } from 'msw'
import createdExample from '../../../../../contracts/examples/admin/create-admin-playlist.201.json'
import listExample from '../../../../../contracts/examples/admin/list-admin-playlists.200.json'
import updatedExample from '../../../../../contracts/examples/admin/update-admin-playlist.200.json'

/** Playlist mocks from contracts/examples/admin. */
export const adminPlaylistHandlers = [
  http.get('*/api/v1/admin/playlists', () => HttpResponse.json(listExample)),
  http.post('*/api/v1/admin/playlists', () => HttpResponse.json(createdExample, { status: 201 })),
  http.put('*/api/v1/admin/playlists/:id', () => HttpResponse.json(updatedExample)),
  http.post('*/api/v1/admin/playlists/:id/tracks', () => HttpResponse.json(updatedExample)),
  http.delete('*/api/v1/admin/playlists/:id/tracks', () => HttpResponse.json(updatedExample)),
]
