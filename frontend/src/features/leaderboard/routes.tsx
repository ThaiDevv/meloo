import type { RouteObject } from 'react-router-dom'
import { LeaderboardPage } from './LeaderboardPage'

export const routes: RouteObject[] = [{ path: '/leaderboard', element: <LeaderboardPage /> }]
