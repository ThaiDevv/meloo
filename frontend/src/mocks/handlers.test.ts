import { beforeEach, describe, expect, it } from 'vitest'
import { resetSoloMock } from '@/features/solo/setup/mocks'
import { apiClient } from '@/shared/api/client'
import getFiltersExample from '../../../contracts/examples/catalog/get-filters.200.json'
import listPlaylistsExample from '../../../contracts/examples/catalog/list-playlists.200.json'

const VALID_PLAYLIST_ID =
  listPlaylistsExample.items[0]?.id ?? 'b9d3e7a1-4c60-4f28-a135-7e2c0b8d9f43'
const CURATED_PLAYLIST_ID =
  listPlaylistsExample.items[1]?.id ?? '14f8a2c6-d570-4e9b-b3a8-c6e1d0f27a95'
const UNKNOWN_PLAYLIST_ID = '99999999-9999-9999-9999-999999999999'

describe('MSW mock server', () => {
  beforeEach(() => {
    resetSoloMock()
  })

  it('answers requests made through the API client', async () => {
    const res = await fetch('/api/health').catch(() => null)
    expect(res).not.toBeNull()
    expect(await res?.json()).toEqual({ status: 'UP' })
    expect(apiClient).toBeDefined()
  })

  describe('GET /api/v1/filters (catalog filters)', () => {
    it('returns 200 with genres, countries, decades, and artists from contract example', async () => {
      const { data, response } = await apiClient.GET('/api/v1/filters')
      expect(response.status).toBe(200)
      expect(data).toEqual(getFiltersExample)
    })
  })

  describe('GET /api/v1/playlists (playlists pagination)', () => {
    it('returns 200 with list-playlists contract example and default pagination', async () => {
      const { data, response } = await apiClient.GET('/api/v1/playlists')
      expect(response.status).toBe(200)
      expect(data).toMatchObject({
        page: 0,
        size: 10,
        totalItems: listPlaylistsExample.items.length,
        totalPages: 1,
        items: listPlaylistsExample.items,
      })
    })

    it('honors page and size query parameters', async () => {
      const { data, response } = await apiClient.GET('/api/v1/playlists', {
        params: { query: { page: 1, size: 1 } },
      })
      expect(response.status).toBe(200)
      expect(data?.page).toBe(1)
      expect(data?.size).toBe(1)
      expect(data?.items).toHaveLength(1)
      expect(data?.items[0]?.id).toBe(CURATED_PLAYLIST_ID)
    })

    it('caps size at 50 per Acceptance Criteria', async () => {
      const { data, response } = await apiClient.GET('/api/v1/playlists', {
        params: { query: { size: 100 } },
      })
      expect(response.status).toBe(200)
      expect(data?.size).toBe(50)
    })
  })

  describe('POST /api/v1/solo/matches (solo match creation)', () => {
    it('rejects RANKED mode with filter with 400 VALIDATION_FAILED', async () => {
      const { response, error } = await apiClient.POST('/api/v1/solo/matches', {
        body: {
          mode: 'RANKED',
          roundCount: 10,
          filter: { genreCodes: ['kpop'] },
        },
      })
      expect(response.status).toBe(400)
      expect(error).toMatchObject({
        status: 400,
        code: 'VALIDATION_FAILED',
      })
    })

    it('rejects RANKED mode with playlistId with 400 VALIDATION_FAILED', async () => {
      const { response, error } = await apiClient.POST('/api/v1/solo/matches', {
        body: {
          mode: 'RANKED',
          roundCount: 10,
          playlistId: VALID_PLAYLIST_ID,
        },
      })
      expect(response.status).toBe(400)
      expect(error).toMatchObject({
        status: 400,
        code: 'VALIDATION_FAILED',
      })
    })

    it('rejects sending both filter and playlistId with 400 VALIDATION_FAILED', async () => {
      const { response, error } = await apiClient.POST('/api/v1/solo/matches', {
        body: {
          mode: 'PRACTICE',
          roundCount: 10,
          filter: { genreCodes: ['vpop'] },
          playlistId: VALID_PLAYLIST_ID,
        },
      })
      expect(response.status).toBe(400)
      expect(error).toMatchObject({
        status: 400,
        code: 'VALIDATION_FAILED',
      })
    })

    it('returns 404 PLAYLIST_NOT_FOUND when playlistId is unknown', async () => {
      const { response, error } = await apiClient.POST('/api/v1/solo/matches', {
        body: {
          mode: 'PRACTICE',
          roundCount: 10,
          playlistId: UNKNOWN_PLAYLIST_ID,
        },
      })
      expect(response.status).toBe(404)
      expect(error).toMatchObject({
        status: 404,
        code: 'PLAYLIST_NOT_FOUND',
      })
    })

    it('triggers 422 NOT_ENOUGH_TRACKS when PRACTICE + kpop genre + 15 rounds', async () => {
      const { response, error } = await apiClient.POST('/api/v1/solo/matches', {
        body: {
          mode: 'PRACTICE',
          roundCount: 15,
          filter: { genreCodes: ['kpop'] },
        },
      })
      expect(response.status).toBe(422)
      expect(error).toMatchObject({
        status: 422,
        code: 'NOT_ENOUGH_TRACKS',
        missingTracks: 4,
      })
    })

    it('creates a RANKED match with 201 and valid body', async () => {
      const { data, response } = await apiClient.POST('/api/v1/solo/matches', {
        body: { mode: 'RANKED', roundCount: 10 },
      })
      expect(response.status).toBe(201)
      expect(data).toMatchObject({
        mode: 'RANKED',
        ranked: true,
        roundCount: 10,
        totalScore: 0,
        comboStreak: 0,
        currentRound: {
          roundNo: 1,
          roundCount: 10,
          level: 1,
          listenSeconds: 2,
          canListenMore: true,
          canSkip: false,
        },
      })
      expect(data?.matchId).toBeDefined()
    })

    it('creates a PRACTICE match with filter', async () => {
      const filter = { genreCodes: ['vpop'], countryCodes: ['VN'] }
      const { data, response } = await apiClient.POST('/api/v1/solo/matches', {
        body: { mode: 'PRACTICE', roundCount: 5, filter },
      })
      expect(response.status).toBe(201)
      expect(data).toMatchObject({
        mode: 'PRACTICE',
        ranked: false,
        roundCount: 5,
        filter,
      })
    })

    it('creates a PRACTICE match with playlistId', async () => {
      const { data, response } = await apiClient.POST('/api/v1/solo/matches', {
        body: {
          mode: 'PRACTICE',
          roundCount: 10,
          playlistId: VALID_PLAYLIST_ID,
        },
      })
      expect(response.status).toBe(201)
      expect(data).toMatchObject({
        mode: 'PRACTICE',
        ranked: false,
        roundCount: 10,
        playlistId: VALID_PLAYLIST_ID,
      })
    })

    it('synchronizes playlistId with GET /api/v1/solo/matches/active', async () => {
      const createRes = await apiClient.POST('/api/v1/solo/matches', {
        body: {
          mode: 'PRACTICE',
          roundCount: 10,
          playlistId: VALID_PLAYLIST_ID,
        },
      })
      expect(createRes.response.status).toBe(201)
      expect(createRes.data?.playlistId).toBe(VALID_PLAYLIST_ID)

      const activeRes = await apiClient.GET('/api/v1/solo/matches/active')
      expect(activeRes.response.status).toBe(200)
      expect(activeRes.data?.matchId).toBe(createRes.data?.matchId)
      expect(activeRes.data?.playlistId).toBe(VALID_PLAYLIST_ID)
    })

    it('returns 409 SOLO_MATCH_IN_PROGRESS when a match is already running', async () => {
      const first = await apiClient.POST('/api/v1/solo/matches', {
        body: { mode: 'RANKED', roundCount: 10 },
      })
      expect(first.response.status).toBe(201)
      const firstMatchId = first.data?.matchId

      const second = await apiClient.POST('/api/v1/solo/matches', {
        body: { mode: 'RANKED', roundCount: 10 },
      })
      expect(second.response.status).toBe(409)
      expect(second.error).toMatchObject({
        status: 409,
        code: 'SOLO_MATCH_IN_PROGRESS',
        matchId: firstMatchId,
      })
    })

    it('resets match state with resetSoloMock() allowing a new match to be created', async () => {
      const first = await apiClient.POST('/api/v1/solo/matches', {
        body: { mode: 'RANKED', roundCount: 10 },
      })
      expect(first.response.status).toBe(201)

      resetSoloMock()

      const second = await apiClient.POST('/api/v1/solo/matches', {
        body: { mode: 'PRACTICE', roundCount: 5 },
      })
      expect(second.response.status).toBe(201)
      expect(second.data?.mode).toBe('PRACTICE')
    })
  })
})
