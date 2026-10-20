import type { RouteObject } from 'react-router-dom'
import { SoloPlayPage } from './play/SoloPlayPage'
import { SoloSetupPage } from './setup/SoloSetupPage'

export const routes: RouteObject[] = [
  { path: '/solo', element: <SoloSetupPage /> },
  { path: '/solo/:sessionId', element: <SoloPlayPage /> },
]
