import type { RouteObject } from 'react-router-dom'
import { AuditPage } from './audit/AuditPage'
import { CatalogPage } from './catalog/CatalogPage'
import { AdminDashboardPage } from './dashboard/AdminDashboardPage'
import { ModerationPage } from './moderation/ModerationPage'
import { PlaylistsPage } from './playlists/PlaylistsPage'
import { AdminShell } from './shell/AdminShell'
import { UsersPage } from './users/UsersPage'

export const routes: RouteObject[] = [
  {
    path: '/admin',
    element: <AdminShell />,
    children: [
      { index: true, element: <AdminDashboardPage /> },
      { path: 'catalog', element: <CatalogPage /> },
      { path: 'playlists', element: <PlaylistsPage /> },
      { path: 'users', element: <UsersPage /> },
      { path: 'moderation', element: <ModerationPage /> },
      { path: 'audit', element: <AuditPage /> },
    ],
  },
]
