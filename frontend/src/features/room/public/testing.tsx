import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import i18n from '@/i18n'
import { useAuthStore } from '@/shared/api/authStore'
import { useSessionStore, type Role } from '@/shared/session/store'
import { PublicRoomsPage } from './PublicRoomsPage'
import { QuickMatchPage } from './QuickMatchPage'
import { Where } from './Where'

export function signInAs(userId: string, role: Role = 'USER') {
  useAuthStore.setState({
    accessToken: 'test-token',
    user: {
      id: userId,
      kind: role === 'GUEST' ? 'GUEST' : 'REGISTERED',
      role: role === 'ADMIN' ? 'ADMIN' : 'USER',
      nickname: 'Tester',
      createdAt: '2026-10-01T00:00:00Z',
    },
  })
  useSessionStore.getState().setRole(role)
}

export function signOutAll() {
  useAuthStore.setState({ accessToken: null, user: null })
  useSessionStore.getState().setRole(null)
}

/** Renders the two pages of FE-ROOM-03 with stand-ins for the screens they lead to. */
export function renderPublic(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/rooms" element={<PublicRoomsPage />} />
            <Route path="/quick-match" element={<QuickMatchPage />} />
            <Route path="/r/:code" element={<Where label="lobby" />} />
            <Route path="/r/:code/play" element={<Where label="play" />} />
            <Route path="/rooms/new" element={<p>tạo phòng</p>} />
            <Route path="/solo" element={<Where label="solo" />} />
            <Route path="/start" element={<Where label="start" />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}
