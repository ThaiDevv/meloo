import { http, HttpResponse } from 'msw'
import dailyExample from '../../../../contracts/examples/daily/get-daily-leaderboard.200.json'
import allTimeExample from '../../../../contracts/examples/progression/get-all-time-leaderboard.200.json'

/** Leaderboard mocks from contracts/examples (CT-03). */
export const leaderboardHandlers = [
  http.get('*/api/v1/leaderboards/all-time', () => HttpResponse.json(allTimeExample)),
  http.get('*/api/v1/leaderboards/daily', () => HttpResponse.json(dailyExample)),
]
