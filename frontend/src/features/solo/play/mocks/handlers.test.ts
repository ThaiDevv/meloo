import { beforeEach, describe, expect, it } from 'vitest'
import { apiClient } from '@/shared/api/client'
import { resetSoloPlayMock } from './handlers'

const A1 = '3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21' // answer of round 1 (and 6, 11)
const A2 = 'c47b0e95-1d2a-4f36-8b59-a0e7d3c61f84'
const A3 = '8d2e5b73-6c14-4a90-b7f2-1e3c9a5d0f48'
const DECOY = '5b1d7e22-3c8e-4f69-82d4-7e1b9a5c8f32' // same artist as round 1

beforeEach(() => {
  resetSoloPlayMock()
})

async function create(mode: 'PRACTICE' = 'PRACTICE', roundCount: 5 | 10 | 15 = 5) {
  const { data } = await apiClient.POST('/api/v1/solo/matches', { body: { mode, roundCount } })
  if (!data) throw new Error('create failed')
  return data
}

function guess(matchId: string, roundNo: number, trackId: string) {
  return apiClient.POST('/api/v1/solo/matches/{matchId}/rounds/{roundNo}/guesses', {
    params: { path: { matchId, roundNo } },
    body: { trackId },
  })
}

function listenMore(matchId: string, roundNo: number, expectedLevel?: number) {
  return apiClient.POST('/api/v1/solo/matches/{matchId}/rounds/{roundNo}/listen-more', {
    params: { path: { matchId, roundNo }, query: expectedLevel ? { expectedLevel } : {} },
  })
}

describe('single-play mock server', () => {
  it('creates a match with round 1 open and keeps it as the active one', async () => {
    const match = await create()
    // eslint-disable-next-line @typescript-eslint/no-deprecated
    expect(match.ranked).toBe(false)
    expect(match.currentRound).toMatchObject({ roundNo: 1, level: 1, listenSeconds: 2 })
    expect(match.currentRound.audio.url).not.toContain(A1)
    const { data } = await apiClient.GET('/api/v1/solo/matches/active')
    expect(data?.matchId).toBe(match.matchId)

    const again = await apiClient.POST('/api/v1/solo/matches', { body: { mode: 'PRACTICE' } })
    expect(again.response.status).toBe(409)
    expect(again.error).toMatchObject({ matchId: match.matchId })
  })

  it('scores BR-02 and BR-08: base by level, +10% then +20% for a run at 2s or 5s', async () => {
    const { matchId } = await create()
    const r1 = await guess(matchId, 1, A1)
    expect(r1.data?.end?.result).toMatchObject({
      roundScore: 1000,
      comboStreak: 1,
      comboPercent: 0,
    })
    await listenMore(matchId, 2, 2)
    const r2 = await guess(matchId, 2, A2)
    expect(r2.data?.end?.result).toMatchObject({
      correctLevel: 2,
      baseScore: 800,
      comboPercent: 10,
      roundScore: 880,
      totalScore: 1880,
    })
    const r3 = await guess(matchId, 3, A3)
    expect(r3.data?.end?.result).toMatchObject({ comboPercent: 20, roundScore: 1200 })
  })

  it('opens the next level on a wrong guess and says when the artist was right', async () => {
    const { matchId } = await create()
    const { data } = await guess(matchId, 1, DECOY)
    expect(data).toMatchObject({ correct: false, rightArtist: true, roundEnded: false })
    expect(data?.round?.level).toBe(2)
  })

  it('guards listen-more with expectedLevel and stops at 30 seconds', async () => {
    const { matchId } = await create()
    expect((await listenMore(matchId, 1, 3)).error).toMatchObject({ code: 'LISTEN_LEVEL_MISMATCH' })
    for (const level of [2, 3, 4, 5]) await listenMore(matchId, 1, level)
    expect((await listenMore(matchId, 1)).error).toMatchObject({ code: 'LISTEN_LEVEL_MAX' })
  })

  it('lets a round be skipped only at 30 seconds', async () => {
    const { matchId } = await create()
    const skip = () =>
      apiClient.POST('/api/v1/solo/matches/{matchId}/rounds/{roundNo}/skip', {
        params: { path: { matchId, roundNo: 1 } },
      })
    expect((await skip()).error).toMatchObject({ code: 'SKIP_NOT_ALLOWED' })
    for (const level of [2, 3, 4, 5]) await listenMore(matchId, 1, level)
    expect((await skip()).data?.result).toMatchObject({ outcome: 'GAVE_UP', roundScore: 0 })
  })

  it('finishes after the last round and then answers the match result', async () => {
    const { matchId } = await create()
    const answers = [A1, A2, A3, '92d5a0e7-3f68-4b1c-8e47-d1c9b6a2f503']
    for (const [index, trackId] of answers.entries()) await guess(matchId, index + 1, trackId)
    const before = await apiClient.GET('/api/v1/solo/matches/{matchId}/result', {
      params: { path: { matchId } },
    })
    expect(before.error).toMatchObject({ code: 'MATCH_NOT_FINISHED' })

    const last = await guess(matchId, 5, '6b0e4f93-a1d7-4c25-9f80-27e5c8d3b1a6')
    expect(last.data?.end).toMatchObject({ matchFinished: true })
    expect(last.data?.end?.nextRound).toBeUndefined()
    const { data } = await apiClient.GET('/api/v1/solo/matches/{matchId}/result', {
      params: { path: { matchId } },
    })
    expect(data).toMatchObject({ status: 'FINISHED', ranked: false, correctCount: 5 })
    expect(data?.rounds).toHaveLength(5)
  })

  it('abandons a practice match (BR-23)', async () => {
    resetSoloPlayMock()
    const practice = await create('PRACTICE')
    await guess(practice.matchId, 1, A1)
    const { data } = await apiClient.POST('/api/v1/solo/matches/{matchId}/abandon', {
      params: { path: { matchId: practice.matchId } },
    })
    expect(data).toMatchObject({ status: 'ABANDONED', ranked: false, totalScore: 1000 })
    expect(data?.rounds).toHaveLength(1)
  })

  it('searches the catalog without accents', async () => {
    const { data } = await apiClient.GET('/api/v1/tracks/search', {
      params: { query: { q: 'chung ta' } },
    })
    expect(data?.items.map((track) => track.title)).toEqual([
      'Chúng Ta Của Hiện Tại',
      'Chúng Ta Của Tương Lai',
    ])
  })
})
