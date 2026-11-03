import { http, HttpResponse } from 'msw'
import type { components } from '@/shared/api/schema.gen'
import listPlaylistsExample from '../../../../../contracts/examples/catalog/list-playlists.200.json'
import validationExample from '../../../../../contracts/examples/common/problem.validation-failed.400.json'
import matchInProgressExample from '../../../../../contracts/examples/game/create-solo-match.409.json'
import notEnoughTracksExample from '../../../../../contracts/examples/game/create-solo-match.422.json'
import playlistNotFoundExample from '../../../../../contracts/examples/game/problem.playlist-not-found.404.json'
import { resetSoloPlayMock, soloMatchHandlers } from '../play/mocks/handlers'

type CreateSoloMatchRequest = components['schemas']['CreateSoloMatchRequest']

const PROBLEM = { 'Content-Type': 'application/problem+json' }
const KNOWN_PLAYLIST_IDS = new Set(listPlaylistsExample.items.map((p) => p.id))

let activePlaylistId: string | null = null

/**
 * Resets the single-player mock state for tests (both setup and play).
 * Analogous to resetAuthMock().
 */
export function resetSoloMock(): void {
  activePlaylistId = null
  resetSoloPlayMock()
}

export const soloSetupHandlers = [
  http.post('*/api/v1/solo/matches', async ({ request }) => {
    const rawBody: unknown = await request.json()
    const body = (rawBody && typeof rawBody === 'object' ? rawBody : {}) as CreateSoloMatchRequest

    // BR-23: RANKED cannot take a filter or playlistId (400 VALIDATION_FAILED)
    if (body.mode === 'RANKED' && (body.filter || body.playlistId)) {
      return HttpResponse.json(validationExample, { status: 400, headers: PROBLEM })
    }

    // BR-23: filter and playlistId are mutually exclusive (400 VALIDATION_FAILED)
    if (body.filter && body.playlistId) {
      return HttpResponse.json(validationExample, { status: 400, headers: PROBLEM })
    }

    // CT-01d: unknown playlistId returns 404 PLAYLIST_NOT_FOUND
    if (body.playlistId && !KNOWN_PLAYLIST_IDS.has(body.playlistId)) {
      return HttpResponse.json(playlistNotFoundExample, { status: 404, headers: PROBLEM })
    }

    // Deterministic trigger: PRACTICE mode + kpop genre + 15 rounds -> 422 NOT_ENOUGH_TRACKS
    const isNotEnoughTracks =
      body.mode === 'PRACTICE' &&
      body.roundCount === 15 &&
      Boolean(body.filter?.genreCodes?.includes('kpop'))

    if (isNotEnoughTracks) {
      return HttpResponse.json(notEnoughTracksExample, { status: 422, headers: PROBLEM })
    }

    // Delegate creation to soloMatchHandlers[0] so the active match is tracked for play
    const playCreateHandler = soloMatchHandlers[0] as unknown as {
      resolver: (info: {
        request: Request
        params: Record<string, string>
        cookies: Record<string, string>
      }) => Promise<Response>
    }

    const delegateRequest = new Request(request.url, {
      method: request.method,
      headers: request.headers,
      body: JSON.stringify(body),
    })

    const response = await playCreateHandler.resolver({
      request: delegateRequest,
      params: {},
      cookies: {},
    })

    // If 409, ensure problem format aligns with create-solo-match.409.json
    if (response.status === 409) {
      const err = (await response.json()) as { matchId?: string }
      return HttpResponse.json(
        {
          ...matchInProgressExample,
          matchId: err.matchId ?? matchInProgressExample.matchId,
        },
        { status: 409, headers: PROBLEM },
      )
    }

    // If 201, synchronize active playlistId and attach playlistId to match response
    if (response.status === 201) {
      activePlaylistId = body.playlistId ?? null
      if (body.playlistId) {
        const match = (await response.json()) as Record<string, unknown>
        return HttpResponse.json({ ...match, playlistId: body.playlistId }, { status: 201 })
      }
    }

    return response
  }),

  http.get('*/api/v1/solo/matches/active', async ({ request }) => {
    const playGetActiveHandler = soloMatchHandlers[1] as unknown as {
      resolver: (info: {
        request: Request
        params: Record<string, string>
        cookies: Record<string, string>
      }) => Promise<Response>
    }

    const response = await playGetActiveHandler.resolver({
      request,
      params: {},
      cookies: {},
    })

    // Synchronize playlistId onto active match if present
    if (response.status === 200 && activePlaylistId) {
      const match = (await response.json()) as Record<string, unknown>
      return HttpResponse.json({ ...match, playlistId: activePlaylistId }, { status: 200 })
    }

    return response
  }),
]
