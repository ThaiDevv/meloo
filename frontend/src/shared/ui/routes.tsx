import type { RouteObject } from 'react-router-dom'
import { UiGalleryPage } from './UiGalleryPage'

const enabled = import.meta.env.DEV || import.meta.env.VITE_PREVIEW === 'true'

export const routes: RouteObject[] = enabled ? [{ path: '/_ui', element: <UiGalleryPage /> }] : []
