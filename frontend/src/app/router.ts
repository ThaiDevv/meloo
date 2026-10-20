import { createBrowserRouter } from 'react-router-dom'
import { appRoutes } from './routes'

// Module-level singleton: creating a data router inside a component leaks one under StrictMode.
export const router = createBrowserRouter(appRoutes)
