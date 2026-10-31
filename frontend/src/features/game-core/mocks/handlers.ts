import { http, HttpResponse } from 'msw'
import { gameAudioHandlers } from './audio'
import { mockResultEmptyCover, mockResultNoCover, mockResultWithCover } from './fixtures'

/**
 * `GET /api/v1/solo/matches/{matchId}/rounds/{roundNo}/result`, answering with all three shapes the
 * answer screen must cope with: round 1 has a cover, round 2 has no `coverUrl` at all, round 3 has
 * the empty string the contract also allows. Any other round has a cover.
 */
export const gameRoundResultHandlers = [
  http.get('*/api/v1/solo/matches/:matchId/rounds/:roundNo/result', ({ params }) => {
    const roundNo = Number(params.roundNo)
    const result =
      roundNo === 2 ? mockResultNoCover : roundNo === 3 ? mockResultEmptyCover : mockResultWithCover
    return HttpResponse.json({ ...result, roundNo })
  }),
]

/** Everything game-core needs from a mock server. FE-SOLO-02 and the daily and room tickets register these. */
export const gameCoreHandlers = [...gameAudioHandlers, ...gameRoundResultHandlers]
