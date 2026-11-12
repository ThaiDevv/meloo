import { http, HttpResponse } from 'msw'
import { pointsForLevel } from '@/features/game-core'
import { MOCK_COVER_URL } from '@/features/game-core/mocks'
import type { components } from '@/shared/api/schema.gen'
import historyExample from '../../../../contracts/examples/daily/daily-history.200.json'

type Schemas = components['schemas']
type Level = Schemas['DailyListenSeconds']

/**
 * A small daily-challenge server for VITE_API_MOCK=true (the Vercel preview) and for tests. It keeps
 * one attempt in memory and judges it the way the contract says (BR-02, BR-04, BR-25). The history
 * is the contract example. The answer is a track of the solo mock catalog, so the search finds it.
 */

const ANSWER: Schemas['Answer'] = {
  trackId: '3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21',
  title: 'Chúng Ta Của Hiện Tại',
  artistName: 'Sơn Tùng M-TP',
  provider: 'DEEZER',
  listenUrl: 'https://www.deezer.com/track/1234567890',
  coverUrl: MOCK_COVER_URL,
}
const LENGTHS: Level[] = [2, 5, 10, 20, 30]
const CHALLENGE_DATE = historyExample.today
const NEXT_RESET = '2026-10-29T17:00:00Z'
const PROBLEM = { 'Content-Type': 'application/problem+json' }

interface Attempt {
  startedAt: string
  level: number
  wrongCount: number
  result: Schemas['DailyResult'] | null
}

let attempt: Attempt | null = null
let streak = { current: historyExample.currentStreak, longest: historyExample.longestStreak }

/** For tests: forget the attempt, so the day is unplayed again. */
export function resetDailyMock(): void {
  attempt = null
  streak = { current: historyExample.currentStreak, longest: historyExample.longestStreak }
}

function problem(status: number, code: string, title: string) {
  return HttpResponse.json(
    { type: 'about:blank', title, status, code },
    { status, headers: PROBLEM },
  )
}

function roundOf(open: Attempt): Schemas['DailyRoundState'] {
  const now = Date.now()
  return {
    challengeDate: CHALLENGE_DATE,
    level: open.level,
    listenSeconds: LENGTHS[open.level - 1] ?? 30,
    canListenMore: open.level < 5,
    canSkip: open.level === 5,
    wrongCount: open.wrongCount,
    audio: {
      url: `/api/v1/audio/mock-daily-level-${String(open.level)}`,
      expiresAt: new Date(now + 2 * 60_000).toISOString(),
    },
    startedAt: open.startedAt,
    expiresAt: new Date(Date.parse(open.startedAt) + 5 * 60_000).toISOString(),
    serverTime: new Date(now).toISOString(),
  }
}

function end(open: Attempt, outcome: 'CORRECT' | 'WRONG_OUT' | 'GAVE_UP'): Schemas['DailyResult'] {
  const solved = outcome === 'CORRECT'
  streak = solved
    ? { current: streak.current + 1, longest: Math.max(streak.longest, streak.current + 1) }
    : { ...streak, current: 0 }
  const result: Schemas['DailyResult'] = {
    challengeDate: CHALLENGE_DATE,
    status: solved ? 'SOLVED' : 'UNSOLVED',
    outcome,
    correctLevel: solved ? open.level : null,
    correctListenSeconds: solved ? (LENGTHS[open.level - 1] ?? 30) : null,
    levels: LENGTHS.map((_, index) =>
      solved && index === open.level - 1
        ? 'CORRECT'
        : index < (solved ? open.level - 1 : 5)
          ? 'WRONG'
          : 'UNOPENED',
    ),
    score: (solved ? pointsForLevel(open.level) : 0) as Schemas['DailyResult']['score'],
    wrongCount: open.wrongCount,
    elapsedMs: Date.now() - Date.parse(open.startedAt),
    rank: solved ? 12 : null,
    currentStreak: streak.current,
    longestStreak: streak.longest,
    nextResetAt: NEXT_RESET,
    answer: ANSWER,
  }
  open.result = result
  return result
}

/** The caller's attempt in progress, or the problem the real server answers. */
function active(): Attempt | Response {
  return attempt && !attempt.result
    ? attempt
    : problem(409, 'ROUND_NOT_ACTIVE', 'No active daily attempt')
}

const api = '*/api/v1/daily'

export const dailyHandlers = [
  http.get(`${api}/today`, () => {
    const base = {
      challengeDate: CHALLENGE_DATE,
      currentStreak: streak.current,
      longestStreak: streak.longest,
      nextResetAt: NEXT_RESET,
      serverTime: new Date().toISOString(),
    }
    if (!attempt) return HttpResponse.json({ ...base, status: 'NOT_STARTED' })
    if (attempt.result) {
      return HttpResponse.json({ ...base, status: attempt.result.status, result: attempt.result })
    }
    return HttpResponse.json({ ...base, status: 'IN_PROGRESS', round: roundOf(attempt) })
  }),

  http.post(`${api}/today/start`, () => {
    if (attempt?.result) return problem(409, 'DAILY_ALREADY_PLAYED', 'Already played today')
    attempt ??= { startedAt: new Date().toISOString(), level: 1, wrongCount: 0, result: null }
    return HttpResponse.json(roundOf(attempt))
  }),

  http.post(`${api}/today/listen-more`, ({ request }) => {
    const open = active()
    if (open instanceof Response) return open
    if (open.level >= 5) return problem(409, 'LISTEN_LEVEL_MAX', 'Already at 30 seconds')
    const expected = new URL(request.url).searchParams.get('expectedLevel')
    if (expected !== null && Number(expected) !== open.level + 1) {
      return problem(409, 'LISTEN_LEVEL_MISMATCH', 'Expected level does not match')
    }
    open.level += 1
    return HttpResponse.json(roundOf(open))
  }),

  http.post(`${api}/today/skip`, () => {
    const open = active()
    if (open instanceof Response) return open
    if (open.level < 5) return problem(409, 'SKIP_NOT_ALLOWED', 'Skip not allowed yet')
    return HttpResponse.json(end(open, 'GAVE_UP'))
  }),

  http.post(`${api}/today/guesses`, async ({ request }) => {
    const open = active()
    if (open instanceof Response) return open
    const { trackId } = (await request.json()) as Schemas['GuessRequest']
    if (trackId === ANSWER.trackId) {
      return HttpResponse.json({
        correct: true,
        rightArtist: false,
        attemptEnded: true,
        result: end(open, 'CORRECT'),
      })
    }
    open.wrongCount += 1
    if (open.level >= 5) {
      return HttpResponse.json({
        correct: false,
        rightArtist: false,
        attemptEnded: true,
        result: end(open, 'WRONG_OUT'),
      })
    }
    open.level += 1
    return HttpResponse.json({
      correct: false,
      rightArtist: false,
      attemptEnded: false,
      round: roundOf(open),
    })
  }),

  http.get(`${api}/result`, () =>
    attempt?.result
      ? HttpResponse.json(attempt.result)
      : problem(409, 'ROUND_NOT_FINISHED', 'Daily attempt not finished'),
  ),

  http.get(`${api}/history`, () => HttpResponse.json(historyExample)),
]
