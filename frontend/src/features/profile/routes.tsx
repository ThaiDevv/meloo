import type { RouteObject } from 'react-router-dom'
import { RequireIdentity } from '@/features/auth/RequireIdentity'
import { MatchDetailPage } from './MatchDetailPage'
import { ProfilePage } from './ProfilePage'

export const routes: RouteObject[] = [
  {
    element: <RequireIdentity />,
    children: [
      { path: '/profile', element: <ProfilePage /> },
      { path: '/profile/matches/:matchId', element: <MatchDetailPage /> },
    ],
  },
]
