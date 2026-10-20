import { useState } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { Providers } from './providers'
import { appRoutes } from './routes'

export function App() {
  const [router] = useState(() => createBrowserRouter(appRoutes))
  return (
    <Providers>
      <RouterProvider router={router} />
    </Providers>
  )
}
