import type { RouteObject } from 'react-router-dom'
import { DailyPage } from './DailyPage'

export const routes: RouteObject[] = [{ path: '/daily', element: <DailyPage /> }]
