import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import i18n from '@/i18n'
import { useAuthStore } from '@/shared/api/authStore'
import { LobbyPage } from '../LobbyPage'

/** Signs a test user in; the lobby tells host from member by this id. */
export function signIn(userId: string, nickname = 'Tester') {
  useAuthStore.setState({
    accessToken: 'test-token',
    user: {
      id: userId,
      kind: 'REGISTERED',
      role: 'USER',
      nickname,
      createdAt: '2026-10-01T00:00:00Z',
    },
  })
}

export function signOut() {
  useAuthStore.setState({ accessToken: null, user: null })
}

/** Renders the room routes of FE-ROOM-01 with stand-ins for the pages they link to. */
export function renderLobby(path: string, state?: unknown) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[{ pathname: path, state }]}>
          <Routes>
            <Route path="/rooms/new" element={<LobbyPage />} />
            <Route path="/r/:code" element={<LobbyPage />} />
            <Route path="/r/:code/play" element={<p>trang chơi</p>} />
            <Route path="/start" element={<p>trang bắt đầu</p>} />
            <Route path="/solo" element={<p>trang chơi đơn</p>} />
            <Route path="/" element={<p>trang chủ</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}
