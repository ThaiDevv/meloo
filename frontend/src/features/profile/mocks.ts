import { http, HttpResponse } from 'msw'
import type { components } from '@/shared/api/schema.gen'
import notFoundExample from '../../../../contracts/examples/common/problem.not-found.404.json'
import matchDetailExample from '../../../../contracts/examples/progression/get-my-match-detail.200.json'
import profileExample from '../../../../contracts/examples/progression/get-my-profile.200.json'
import statsExample from '../../../../contracts/examples/progression/get-my-stats.200.json'
import matchesExample from '../../../../contracts/examples/progression/list-my-matches.200.json'

type MatchHistoryItem = components['schemas']['MatchHistoryItem']
type MatchHistoryDetail = components['schemas']['MatchHistoryDetail']

const PROBLEM = { 'Content-Type': 'application/problem+json' }

/** How many matches the mock history holds: enough for two pages of 10. */
const MOCK_MATCH_COUNT = 13

/** The two contract examples, repeated with new ids and older dates so the preview can page. */
function mockHistory(): MatchHistoryItem[] {
  const base = matchesExample.items as MatchHistoryItem[]
  return Array.from({ length: MOCK_MATCH_COUNT }, (_, i) => {
    const source = base[i % base.length] as MatchHistoryItem
    if (i < base.length) return source
    const shift = i * 86_400_000
    return {
      ...source,
      matchId: `00000000-0000-4000-8000-${String(i).padStart(12, '0')}`,
      startedAt: new Date(Date.parse(source.startedAt) - shift).toISOString(),
      endedAt: new Date(Date.parse(source.endedAt) - shift).toISOString(),
    }
  })
}

const history = mockHistory()

/** Mock handlers for /me/profile, /me/stats and /me/matches, from contracts/examples/progression. */
export const profileHandlers = [
  http.get('*/api/v1/me/profile', () => HttpResponse.json(profileExample)),
  http.get('*/api/v1/me/stats', () => HttpResponse.json(statsExample)),
  http.get('*/api/v1/me/matches', ({ request }) => {
    const url = new URL(request.url)
    const page = Math.max(0, Number(url.searchParams.get('page') ?? '0') || 0)
    const size = Math.min(50, Math.max(1, Number(url.searchParams.get('size') ?? '10') || 10))
    return HttpResponse.json({
      page,
      size,
      totalItems: history.length,
      totalPages: Math.ceil(history.length / size),
      items: history.slice(page * size, page * size + size),
    })
  }),
  http.get('*/api/v1/me/matches/:matchId', ({ params }) => {
    const item = history.find((m) => m.matchId === params.matchId)
    if (!item) return HttpResponse.json(notFoundExample, { status: 404, headers: PROBLEM })
    const detail = matchDetailExample as MatchHistoryDetail
    const solo = item.type === 'SOLO'
    return HttpResponse.json({
      ...detail,
      ...item,
      players: solo ? detail.players.slice(0, 1) : detail.players,
    } satisfies MatchHistoryDetail)
  }),
]
