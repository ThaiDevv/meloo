import { http, HttpResponse } from 'msw'
import { adminUsersHandlers } from '@/features/admin/users/mocks'
import { adminModerationHandlers } from '@/features/admin/moderation/mocks'
import { adminAuditHandlers } from '@/features/admin/audit/mocks'
import { adminDashboardHandlers } from '@/features/admin/dashboard/mocks'
import { adminCatalogHandlers } from '@/features/admin/catalog/mocks'
import { adminPlaylistHandlers } from '@/features/admin/playlists/mocks'
import { dailyHandlers } from '@/features/daily/mocks'
import { authHandlers } from '@/features/auth/mocks'
import { leaderboardHandlers } from '@/features/leaderboard/mocks'
import { profileHandlers } from '@/features/profile/mocks'
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
  ...profileHandlers,
  ...leaderboardHandlers,
  ...dailyHandlers,
  ...adminDashboardHandlers,
  ...adminCatalogHandlers,
  ...adminPlaylistHandlers,
  ...adminUsersHandlers,
  ...adminModerationHandlers,
  ...adminAuditHandlers,
]

// MSW cannot fake a WebSocket, so mock mode swaps the STOMP client for a pretend broker.
// Tests do not set the flag and install their own fake.
if (import.meta.env.VITE_API_MOCK === 'true') installMockRealtime()
