import { http, HttpResponse } from 'msw'
import { authHandlers } from '@/features/auth/mocks'
import { roomHandlers } from '@/features/room/lobby/mocks'
import { soloPlayHandlers } from '@/features/solo/play/mocks/handlers'
import { soloSetupHandlers } from '@/features/solo/setup/mocks'
import { musicPickerHandlers } from '@/shared/music-picker/mocks'
import { installMockRealtime } from '@/shared/realtime/mockRoomServer'

/**
 * Mock handlers, enabled with VITE_API_MOCK=true. Feature tickets add handlers here
 * from contracts/examples/**.
 */
export const handlers = [
  http.get('/api/health', () => HttpResponse.json({ status: 'UP' })),
  ...authHandlers,
  ...musicPickerHandlers,
  ...soloSetupHandlers,
  ...soloPlayHandlers,
  ...roomHandlers,
]

// MSW cannot fake a WebSocket, so mock mode swaps the STOMP client for a pretend broker.
// Tests do not set the flag and install their own fake.
if (import.meta.env.VITE_API_MOCK === 'true') installMockRealtime()
