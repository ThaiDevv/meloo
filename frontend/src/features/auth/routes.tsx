import type { RouteObject } from 'react-router-dom'
import { GuestStartPage } from './guest/GuestStartPage'
import { OAuthCallbackPage } from './oauth/OAuthCallbackPage'

export const routes: RouteObject[] = [
  { path: '/start', element: <GuestStartPage /> },
  { path: '/auth/callback', element: <OAuthCallbackPage /> },
]
