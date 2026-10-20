import type { RouteObject } from 'react-router-dom'
import { LobbyPage } from './lobby/LobbyPage'
import { MatchPage } from './match/MatchPage'
import { PublicRoomsPage } from './public/PublicRoomsPage'
import { QuickMatchPage } from './public/QuickMatchPage'

export const routes: RouteObject[] = [
  { path: '/rooms/new', element: <LobbyPage /> },
  { path: '/r/:code', element: <LobbyPage /> },
  { path: '/r/:code/play', element: <MatchPage /> },
  { path: '/rooms', element: <PublicRoomsPage /> },
  { path: '/quick-match', element: <QuickMatchPage /> },
]
