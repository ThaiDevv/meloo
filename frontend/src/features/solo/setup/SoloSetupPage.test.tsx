import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import filtersExample from '../../../../../contracts/examples/catalog/get-filters.200.json'
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
  it('starts a ranked match of 10 rounds by default, without a filter', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Chơi đơn' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    expect(await screen.findByText('match page')).toBeInTheDocument()
    expect(bodies).toEqual([{ mode: 'RANKED', roundCount: 10 }])
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
    expect(bodies).toEqual([{ mode: 'RANKED', roundCount: 15 }])
  })

  it('sends the chosen filter in practice mode', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderPage()

    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /Luyện tập/ }))
    await user.click(await screen.findByRole('button', { name: 'K-pop' }))
    await user.click(screen.getByRole('button', { name: 'Hàn Quốc' }))
    await user.click(screen.getByRole('button', { name: 'Vào trận' }))

    await screen.findByText('match page')
    expect(bodies).toEqual([
      { mode: 'PRACTICE', roundCount: 10, filter: { genreCodes: ['kpop'], countryCodes: ['KR'] } },
    ])
  })

  it('keeps the playlist tab locked until the contract can carry a playlist', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('radio', { name: /Luyện tập/ }))
    expect(await screen.findByRole('tab', { name: /Bộ đề/ })).toBeDisabled()
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
