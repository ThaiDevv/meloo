import { http, HttpResponse } from 'msw'
import importExample from '../../../../../contracts/examples/admin/import-admin-tracks.200.json'
import tracksExample from '../../../../../contracts/examples/admin/list-admin-tracks.200.json'
import statusExample from '../../../../../contracts/examples/admin/set-admin-track-status.200.json'

/** Catalog mocks from contracts/examples/admin. */
export const adminCatalogHandlers = [
  http.get('*/api/v1/admin/tracks', () => HttpResponse.json(tracksExample)),
  http.put('*/api/v1/admin/tracks/:trackId/status', () => HttpResponse.json(statusExample)),
  http.post('*/api/v1/admin/tracks/import', () => HttpResponse.json(importExample)),
]
