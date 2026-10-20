import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from '@/app/App'
import { initMonitoring } from '@/shared/monitoring'
import './index.css'

initMonitoring()

async function enableMocking(): Promise<void> {
  if (import.meta.env.VITE_API_MOCK !== 'true') return
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledFrame: 'bypass' })
}

const root = document.getElementById('root')
if (!root) throw new Error('Missing #root element')

void enableMocking().then(() => {
  createRoot(root).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
