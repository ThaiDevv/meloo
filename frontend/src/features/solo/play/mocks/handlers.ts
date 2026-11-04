import { http, HttpResponse } from 'msw'
import { foldDiacritics, pointsForLevel, type RoundResult } from '@/features/game-core'
import { MOCK_COVER_URL, gameAudioHandlers, gameCoverHandlers } from '@/features/game-core/mocks'
import type { components } from '@/shared/api/schema.gen'
import type { MatchResult, RoundState, SoloMatch } from '../api/soloMatchApi'

type Schemas = components['schemas']
type Answer = Schemas['Answer']

/**
 * A small single-play server for VITE_API_MOCK=true (the Vercel preview) and for tests. It keeps one
 * match in memory and judges and scores it the way the contract says the real server does (BR-02,
 * BR-04, BR-08), so the screen has real responses to show. The answers rotate through the tracks of
 * contracts/examples/game; search covers them and two decoys by the same artist (BR-06).
 */

const ANSWERS: Answer[] = [
  {
    trackId: '3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21',
    title: 'Chúng Ta Của Hiện Tại',
    artistName: 'Sơn Tùng M-TP',
    provider: 'DEEZER',
    listenUrl: 'https://www.deezer.com/track/1234567890',
    coverUrl: MOCK_COVER_URL,
  },
  {
    trackId: 'c47b0e95-1d2a-4f36-8b59-a0e7d3c61f84',
    title: 'Waiting For You',
    artistName: 'MONO',
    provider: 'DEEZER',
    listenUrl: 'https://www.deezer.com/track/2345678901',
  },
  {
    trackId: '8d2e5b73-6c14-4a90-b7f2-1e3c9a5d0f48',
    title: 'Em Của Ngày Hôm Qua',
    artistName: 'Sơn Tùng M-TP',
    provider: 'DEEZER',
    listenUrl: 'https://www.deezer.com/track/3456789012',
    coverUrl: MOCK_COVER_URL,
  },
  {
    trackId: '92d5a0e7-3f68-4b1c-8e47-d1c9b6a2f503',
    title: 'Em Là Của Anh',
    artistName: 'MONO',
    provider: 'DEEZER',
    listenUrl: 'https://www.deezer.com/track/4567890123',
    coverUrl: '',
  },
  {
    trackId: '6b0e4f93-a1d7-4c25-9f80-27e5c8d3b1a6',
    title: 'Hãy Trao Cho Anh',
    artistName: 'Sơn Tùng M-TP',
    provider: 'DEEZER',
    listenUrl: 'https://www.deezer.com/track/5678901234',
    coverUrl: MOCK_COVER_URL,
  },
]

const CATALOG: Schemas['TrackSuggestion'][] = [
  ...ANSWERS.map(({ trackId, title, artistName }) => ({ id: trackId, title, artistName })),
  {
    id: '5b1d7e22-3c8e-4f69-82d4-7e1b9a5c8f32',
    title: 'Chúng Ta Của Tương Lai',
    artistName: 'Sơn Tùng M-TP',
  },
  {
    id: '7c2e8f33-4d9f-4a70-93e5-8f2cab6d9a43',
    title: 'Chạy Ngay Đi',
    artistName: 'Sơn Tùng M-TP',
  },
]

const LENGTHS = [2, 5, 10, 20, 30] as const
const PROBLEM = { 'Content-Type': 'application/problem+json' }

interface MockMatch {
  matchId: string
  mode: Schemas['SoloMode']
  roundCount: 5 | 10 | 15
  filter?: Schemas['SoloFilter']
  totalScore: number
  comboStreak: number
  startedAt: string
  roundNo: number
  level: number
  wrongCount: number
  rounds: RoundResult[]
}

let active: MockMatch | null = null
const ended = new Map<string, MatchResult>()

/** For tests: forget every mock match. */
export function resetSoloPlayMock(): void {
  active = null
  ended.clear()
}

function problem(status: number, code: string, title: string, extra: object = {}) {
  return HttpResponse.json(
    { type: 'about:blank', title, status, code, ...extra },
    { status, headers: PROBLEM },
  )
}

function answerOf(roundNo: number): Answer {
  return ANSWERS[(roundNo - 1) % ANSWERS.length] ?? (ANSWERS[0] as Answer)
}

function roundState(match: MockMatch): RoundState {
  const now = Date.now()
  return {
    matchId: match.matchId,
    roundNo: match.roundNo,
    roundCount: match.roundCount,
    level: match.level,
    listenSeconds: LENGTHS[match.level - 1] ?? 30,
    canListenMore: match.level < 5,
    canSkip: match.level === 5,
    audio: {
      url: `/api/v1/audio/mock-level-${String(match.level)}-round-${String(match.roundNo)}-${match.matchId.slice(0, 8)}`,
      expiresAt: new Date(now + 2 * 60_000).toISOString(),
    },
    serverTime: new Date(now).toISOString(),
  }
}

function soloMatch(match: MockMatch): SoloMatch {
  return {
    matchId: match.matchId,
    mode: match.mode,
    // eslint-disable-next-line @typescript-eslint/no-deprecated
    ranked: false,
    roundCount: match.roundCount,
    ...(match.filter && { filter: match.filter }),
    totalScore: match.totalScore,
    comboStreak: match.comboStreak,
    startedAt: match.startedAt,
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    currentRound: roundState(match),
  }
}

function close(match: MockMatch, status: Schemas['MatchStatus']): MatchResult {
  const result: MatchResult = {
    matchId: match.matchId,
    mode: match.mode,
    // eslint-disable-next-line @typescript-eslint/no-deprecated
    ranked: false,
    status,
    roundCount: match.roundCount,
    totalScore: match.totalScore,
    correctCount: match.rounds.filter((round) => round.outcome === 'CORRECT').length,
    rounds: match.rounds,
    startedAt: match.startedAt,
    endedAt: new Date().toISOString(),
  }
  ended.set(match.matchId, result)
  active = null
  return result
}

/** BR-02 base points, BR-08 combo: right at 2s or 5s chains; the 2nd in a row +10%, from the 3rd +20%. */
function endRound(match: MockMatch, outcome: 'CORRECT' | 'WRONG_OUT' | 'GAVE_UP') {
  const correct = outcome === 'CORRECT'
  const chains = correct && match.level <= 2
  const streak = chains ? match.comboStreak + 1 : 0
  const comboPercent = chains ? (streak >= 3 ? 20 : streak === 2 ? 10 : 0) : 0
  const baseScore = correct ? pointsForLevel(match.level) : 0
  const roundScore = baseScore + Math.round((baseScore * comboPercent) / 100)
  match.comboStreak = streak
  match.totalScore += roundScore
  const result: RoundResult = {
    roundNo: match.roundNo,
    outcome,
    ...(correct && { correctLevel: match.level }),
    baseScore: baseScore as RoundResult['baseScore'],
    comboPercent: comboPercent,
    speedBonus: 0,
    roundScore,
    wrongCount: match.wrongCount,
    comboStreak: streak,
    totalScore: match.totalScore,
    answer: answerOf(match.roundNo),
  }
  match.rounds = [...match.rounds, result]
  if (match.roundNo >= match.roundCount) {
    close(match, 'FINISHED')
    return { result, matchFinished: true }
  }
  match.roundNo += 1
  match.level = 1
  match.wrongCount = 0
  return { result, matchFinished: false, nextRound: roundState(match) }
}

/** The caller's match in progress for this id and round, or the problem the real server answers. */
function openRound(matchId: string, roundNo?: number): MockMatch | Response {
  if (active?.matchId !== matchId) {
    return ended.has(matchId)
      ? problem(409, 'MATCH_NOT_IN_PROGRESS', 'Match not in progress')
      : problem(404, 'MATCH_NOT_FOUND', 'Match not found')
  }
  if (roundNo !== undefined && roundNo !== active.roundNo) {
    return problem(409, 'ROUND_NOT_ACTIVE', 'Round not active')
  }
  return active
}

const api = '*/api/v1'

export const soloMatchHandlers = [
  http.post(`${api}/solo/matches`, async ({ request }) => {
    const body = (await request.json()) as Schemas['CreateSoloMatchRequest']
    if (active) {
      return problem(409, 'SOLO_MATCH_IN_PROGRESS', 'Match in progress', {
        matchId: active.matchId,
      })
    }
    active = {
      matchId: crypto.randomUUID(),
      mode: body.mode,
      roundCount: body.roundCount ?? 10,
      ...(body.filter && { filter: body.filter }),
      totalScore: 0,
      comboStreak: 0,
      startedAt: new Date().toISOString(),
      roundNo: 1,
      level: 1,
      wrongCount: 0,
      rounds: [],
    }
    return HttpResponse.json(soloMatch(active), { status: 201 })
  }),

  http.get(`${api}/solo/matches/active`, () =>
    active
      ? HttpResponse.json(soloMatch(active))
      : problem(404, 'NO_ACTIVE_MATCH', 'No active match'),
  ),

  http.get(`${api}/solo/matches/:matchId/rounds/current`, ({ params }) => {
    const match = openRound(String(params.matchId))
    return match instanceof Response ? match : HttpResponse.json(roundState(match))
  }),

  http.post(`${api}/solo/matches/:matchId/rounds/:roundNo/listen-more`, ({ params, request }) => {
    const match = openRound(String(params.matchId), Number(params.roundNo))
    if (match instanceof Response) return match
    if (match.level >= 5) return problem(409, 'LISTEN_LEVEL_MAX', 'Already at 30 seconds')
    const expected = new URL(request.url).searchParams.get('expectedLevel')
    if (expected !== null && Number(expected) !== match.level + 1) {
      return problem(409, 'LISTEN_LEVEL_MISMATCH', 'Expected level does not match')
    }
    match.level += 1
    return HttpResponse.json(roundState(match))
  }),

  http.post(`${api}/solo/matches/:matchId/rounds/:roundNo/skip`, ({ params }) => {
    const match = openRound(String(params.matchId), Number(params.roundNo))
    if (match instanceof Response) return match
    if (match.level < 5) return problem(409, 'SKIP_NOT_ALLOWED', 'Skip not allowed yet')
    return HttpResponse.json(endRound(match, 'GAVE_UP'))
  }),

  http.post(`${api}/solo/matches/:matchId/rounds/:roundNo/guesses`, async ({ params, request }) => {
    const match = openRound(String(params.matchId), Number(params.roundNo))
    if (match instanceof Response) return match
    const { trackId } = (await request.json()) as Schemas['GuessRequest']
    const picked = CATALOG.find((track) => track.id === trackId)
    if (!picked) return problem(404, 'TRACK_NOT_FOUND', 'Track not found')
    const answer = answerOf(match.roundNo)
    // BR-05: the same title after normalisation is right, whichever recording was picked.
    if (foldDiacritics(picked.title) === foldDiacritics(answer.title)) {
      return HttpResponse.json({
        correct: true,
        rightArtist: false,
        roundEnded: true,
        end: endRound(match, 'CORRECT'),
      })
    }
    const rightArtist = picked.artistName === answer.artistName
    match.wrongCount += 1
    if (match.level >= 5) {
      return HttpResponse.json({
        correct: false,
        rightArtist,
        roundEnded: true,
        end: endRound(match, 'WRONG_OUT'),
      })
    }
    match.level += 1
    return HttpResponse.json({
      correct: false,
      rightArtist,
      roundEnded: false,
      round: roundState(match),
    })
  }),

  http.get(`${api}/solo/matches/:matchId/result`, ({ params }) => {
    const matchId = String(params.matchId)
    const result = ended.get(matchId)
    if (result) return HttpResponse.json(result)
    return active?.matchId === matchId
      ? problem(409, 'MATCH_NOT_FINISHED', 'Match not finished')
      : problem(404, 'MATCH_NOT_FOUND', 'Match not found')
  }),

  http.post(`${api}/solo/matches/:matchId/abandon`, ({ params }) => {
    const match = openRound(String(params.matchId))
    if (match instanceof Response) return match
    return HttpResponse.json(close(match, 'ABANDONED'))
  }),

  http.get(`${api}/tracks/search`, ({ request }) => {
    const needle = foldDiacritics(new URL(request.url).searchParams.get('q') ?? '')
    const items = needle
      ? CATALOG.filter((track) =>
          foldDiacritics(`${track.title} ${track.artistName}`).includes(needle),
        )
      : []
    return HttpResponse.json({
      page: 0,
      size: 10,
      totalItems: items.length,
      totalPages: 1,
      items,
    })
  }),
]

/** Everything single play needs from a mock server, registered in src/mocks/handlers.ts. */
export const soloPlayHandlers = [...soloMatchHandlers, ...gameAudioHandlers, ...gameCoverHandlers]
