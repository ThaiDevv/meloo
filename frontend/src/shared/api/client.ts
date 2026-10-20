// Contract paths already start with /api/v1, so the base URL is the backend origin
// (empty = same origin). Never put /api here: openapi-fetch would double it.
import createClient from 'openapi-fetch'
import type { paths } from './schema.gen'

export function createApiClient(baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? '') {
  return createClient<paths>({ baseUrl })
}

export const apiClient = createApiClient()
