import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import filtersExample from '../../../../../contracts/examples/catalog/get-filters.200.json'
import listPlaylistsExample from '../../../../../contracts/examples/catalog/list-playlists.200.json'
import matchExample from '../../../../../contracts/examples/game/create-solo-match.201.json'
import { SoloSetupPage } from './SoloSetupPage'

function renderPage() {
  server.use(http.get('/api/v1/filters', () => HttpResponse.json(filtersExample)))
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={['/solo']}>
          <Routes>
            <Route path="/solo" element={<SoloSetupPage />} />
            <Route path="/solo/:matchId" element={<p>match page</p>} />
            <Route path="/start" element={<p>start page</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

/** Answers POST /solo/matches and returns the bodies it received. */
function captureCreate(
  respond: () => Response = () => HttpResponse.json(matchExample, { status: 201 }),
) {
  const bodies: unknown[] = []
  server.use(
    http.post('/api/v1/solo/matches', async ({ request }) => {
      bodies.push(await request.json())
      return respond()
    }),
  )
  return bodies
}

const problem = { 'Content-Type': 'application/problem+json' }

describe('SoloSetupPage', () => {
  it('starts a match of 10 rounds by default, without a filter', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Chơi đơn' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    expect(await screen.findByText('match page')).toBeInTheDocument()
    expect(bodies).toEqual([{ mode: 'PRACTICE', roundCount: 10 }])
  })

  it('offers 5, 10 and 15 rounds', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderPage()

    expect(
      screen.getAllByRole('radio', { name: /^(5|10|15)/ }).map((r) => r.getAttribute('value')),
    ).toEqual(['5', '10', '15'])
    await user.click(screen.getByRole('radio', { name: /^15/ }))
    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    await screen.findByText('match page')
    expect(bodies).toEqual([{ mode: 'PRACTICE', roundCount: 15 }])
  })

  it('sends the chosen filter in practice mode', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderPage()

    expect(screen.getByRole('tablist')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'K-pop' }))
    await user.click(screen.getByRole('button', { name: 'Hàn Quốc' }))
    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    await screen.findByText('match page')
    expect(bodies).toEqual([
      { mode: 'PRACTICE', roundCount: 10, filter: { genreCodes: ['kpop'], countryCodes: ['KR'] } },
    ])
  })

  it('locks the start button with explanation when playlist tab is selected but no playlist is chosen', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('radio', { name: /Luyện tập/ }))
    const playlistTab = await screen.findByRole('tab', { name: /Bộ đề/ })
    expect(playlistTab).not.toBeDisabled()

    await user.click(playlistTab)
    const summary = screen.getByRole('complementary')
    const startButton = screen.getByRole('button', { name: 'Vào trận' })
    expect(startButton).toBeDisabled()
    expect(screen.getByText('Hãy chọn một bộ đề để vào trận.')).toBeInTheDocument()
    expect(within(summary).getByText('Chưa chọn bộ đề')).toBeInTheDocument()

    // Pick a playlist
    const playlistOption = await screen.findByRole('button', { name: /V-pop thập niên 2010/ })
    await user.click(playlistOption)

    expect(startButton).not.toBeDisabled()
    expect(screen.queryByText('Hãy chọn một bộ đề để vào trận.')).not.toBeInTheDocument()
    expect(within(summary).getByText('V-pop thập niên 2010')).toBeInTheDocument()

    // Deselect playlist
    await user.click(playlistOption)
    expect(startButton).toBeDisabled()
    expect(screen.getByText('Hãy chọn một bộ đề để vào trận.')).toBeInTheDocument()
    expect(within(summary).getByText('Chưa chọn bộ đề')).toBeInTheDocument()
  })

  it('sends playlistId and no filter when starting with a playlist in practice mode', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderPage()

    await user.click(screen.getByRole('radio', { name: /Luyện tập/ }))
    await user.click(await screen.findByRole('tab', { name: /Bộ đề/ }))
    await user.click(await screen.findByRole('button', { name: /V-pop thập niên 2010/ }))
    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    await screen.findByText('match page')
    expect(bodies).toEqual([
      {
        mode: 'PRACTICE',
        roundCount: 10,
        playlistId: '14f8a2c6-d570-4e9b-b3a8-c6e1d0f27a95',
      },
    ])
    expect((bodies[0] as Record<string, unknown>).filter).toBeUndefined()
  })

  it('displays playlist name in English when language is switched', async () => {
    const user = userEvent.setup()
    await act(async () => {
      await i18n.changeLanguage('en')
    })
    try {
      renderPage()
      await user.click(screen.getByRole('radio', { name: /Practice/ }))
      await user.click(await screen.findByRole('tab', { name: /Playlists/ }))
      await user.click(await screen.findByRole('button', { name: /V-pop of the 2010s/ }))

      const summary = screen.getByRole('complementary')
      expect(within(summary).getByText('V-pop of the 2010s')).toBeInTheDocument()
    } finally {
      await act(async () => {
        await i18n.changeLanguage('vi')
      })
    }
  })

  it('handles 404 PLAYLIST_NOT_FOUND by showing an alert and refetching playlists', async () => {
    const user = userEvent.setup()
    let playlistFetchCount = 0
    server.use(
      http.get('*/api/v1/playlists', () => {
        playlistFetchCount++
        return HttpResponse.json(listPlaylistsExample)
      }),
      http.post('/api/v1/solo/matches', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Playlist not found',
            status: 404,
            code: 'PLAYLIST_NOT_FOUND',
            detail: 'The playlist was not found or has been disabled.',
          },
          { status: 404, headers: problem },
        ),
      ),
    )
    renderPage()

    await user.click(screen.getByRole('radio', { name: /Luyện tập/ }))
    await user.click(await screen.findByRole('tab', { name: /Bộ đề/ }))
    await user.click(await screen.findByRole('button', { name: /V-pop thập niên 2010/ }))

    const initialFetches = playlistFetchCount
    expect(initialFetches).toBeGreaterThanOrEqual(1)

    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('không còn khả dụng')
    expect(playlistFetchCount).toBeGreaterThan(initialFetches)
  })

  it('offers to resume when a match is already in progress', async () => {
    const user = userEvent.setup()
    captureCreate(() =>
      HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Conflict',
          status: 409,
          code: 'SOLO_MATCH_IN_PROGRESS',
          matchId: '3f0c6a52-8d1e-4b7a-9c24-5e6f7a8b9c01',
        },
        { status: 409, headers: problem },
      ),
    )
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('đang có một trận chưa xong')
    expect(screen.getByRole('link', { name: 'Chơi tiếp' })).toHaveAttribute(
      'href',
      '/solo/3f0c6a52-8d1e-4b7a-9c24-5e6f7a8b9c01',
    )
  })

  it('says how many songs are missing when the set is too small', async () => {
    const user = userEvent.setup()
    captureCreate(() =>
      HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Unprocessable',
          status: 422,
          code: 'NOT_ENOUGH_TRACKS',
          missingTracks: 3,
        },
        { status: 422, headers: problem },
      ),
    )
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('thiếu 3 bài')
  })

  it('sends a signed-out player to /start', async () => {
    const user = userEvent.setup()
    captureCreate(() =>
      HttpResponse.json(
        { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' },
        { status: 401, headers: problem },
      ),
    )
    server.use(http.post('/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })))
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    expect(await screen.findByText('start page')).toBeInTheDocument()
  })
})
