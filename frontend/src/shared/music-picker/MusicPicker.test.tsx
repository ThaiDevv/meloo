import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import filtersExample from '../../../../contracts/examples/catalog/get-filters.200.json'
import playlistsExample from '../../../../contracts/examples/catalog/list-playlists.200.json'
import { MusicPicker } from './MusicPicker'
import { EMPTY_SELECTION, toSoloFilter, type MusicSelection } from './types'

let latest: MusicSelection = EMPTY_SELECTION

function Harness({ playlistsLocked = false }: { playlistsLocked?: boolean }) {
  const [value, setValue] = useState(EMPTY_SELECTION)
  return (
    <MusicPicker
      value={value}
      onChange={(next) => {
        latest = next
        setValue(next)
      }}
      playlistsLocked={playlistsLocked}
    />
  )
}

function renderPicker(
  playlistsLocked = false,
  filters: () => Response = () => HttpResponse.json(filtersExample),
) {
  latest = EMPTY_SELECTION
  server.use(
    http.get('/api/v1/filters', filters),
    http.get('/api/v1/playlists', () => HttpResponse.json(playlistsExample)),
  )
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <Harness playlistsLocked={playlistsLocked} />
    </QueryClientProvider>,
  )
}

describe('MusicPicker', () => {
  it('offers genre, country, decade and artist from the catalog', async () => {
    renderPicker()

    expect(await screen.findByRole('button', { name: 'V-pop' })).toBeInTheDocument()
    for (const group of ['Thể loại', 'Quốc gia', 'Thập niên', 'Ca sĩ']) {
      expect(screen.getByRole('group', { name: new RegExp(group) })).toBeInTheDocument()
    }
    expect(screen.getByRole('button', { name: 'Việt Nam' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thập niên 2010' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sơn Tùng M-TP' })).toBeInTheDocument()
  })

  it('toggles a value on and off and reports it in the contract shape', async () => {
    const user = userEvent.setup()
    renderPicker()

    const vpop = await screen.findByRole('button', { name: 'V-pop' })
    await user.click(vpop)
    await user.click(screen.getByRole('button', { name: 'Thập niên 2010' }))
    expect(vpop).toHaveAttribute('aria-pressed', 'true')
    expect(toSoloFilter(latest.filter)).toEqual({ genreCodes: ['vpop'], decades: [2010] })

    await user.click(vpop)
    expect(toSoloFilter(latest.filter)).toEqual({ decades: [2010] })
  })

  it('narrows the artist list by an accent-insensitive search', async () => {
    const user = userEvent.setup()
    renderPicker()

    await user.type(await screen.findByRole('searchbox', { name: 'Tìm ca sĩ' }), 'son tung')
    const artists = screen.getByRole('group', { name: /Ca sĩ/ })
    expect(within(artists).getByRole('button', { name: 'Sơn Tùng M-TP' })).toBeInTheDocument()
    expect(within(artists).queryByRole('button', { name: 'MONO' })).not.toBeInTheDocument()
  })

  it('picks a curated playlist from the playlist tab', async () => {
    const user = userEvent.setup()
    renderPicker()

    await user.click(await screen.findByRole('tab', { name: 'Bộ đề' }))
    await user.click(await screen.findByRole('button', { name: /V-pop thập niên 2010/ }))
    expect(latest.source).toBe('playlist')
    expect(latest.playlistId).toBe('14f8a2c6-d570-4e9b-b3a8-c6e1d0f27a95')
  })

  it('locks the playlist tab when the screen cannot send a playlist', async () => {
    renderPicker(true)

    const tab = await screen.findByRole('tab', { name: /Bộ đề/ })
    expect(tab).toBeDisabled()
    expect(tab).toHaveTextContent('Sắp có')
  })

  it('shows an error with a retry when the catalog cannot load', async () => {
    const user = userEvent.setup()
    let fail = true
    renderPicker(false, () =>
      fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(filtersExample),
    )

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('button', { name: 'V-pop' })).toBeInTheDocument()
  })
})
