import type { RouteObject } from 'react-router-dom'
import { DataDeletionPage } from './DataDeletionPage'
import { PrivacyPage } from './PrivacyPage'
import { TermsPage } from './TermsPage'

export const routes: RouteObject[] = [
  { path: '/legal/privacy', element: <PrivacyPage /> },
  { path: '/legal/terms', element: <TermsPage /> },
  { path: '/legal/data-deletion', element: <DataDeletionPage /> },
]
