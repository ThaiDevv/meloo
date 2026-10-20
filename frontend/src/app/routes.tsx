import type { RouteObject } from 'react-router-dom'
import { routes as admin } from '@/features/admin/routes'
import { routes as auth } from '@/features/auth/routes'
import { routes as daily } from '@/features/daily/routes'
import { routes as home } from '@/features/home/routes'
import { routes as leaderboard } from '@/features/leaderboard/routes'
import { routes as legal } from '@/features/legal/routes'
import { routes as profile } from '@/features/profile/routes'
import { routes as room } from '@/features/room/routes'
import { routes as solo } from '@/features/solo/routes'
import { routes as ui } from '@/shared/ui/routes'
import { AppLayout } from './AppLayout'
import { NotFoundPage } from './NotFoundPage'

/** Feature routes are declared in each feature's routes.tsx; only import them here. */
export const appRoutes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      ...home,
      ...auth,
      ...solo,
      ...daily,
      ...room,
      ...profile,
      ...leaderboard,
      ...admin,
      ...legal,
      ...ui,
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
