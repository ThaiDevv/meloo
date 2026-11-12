import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { renderPublic, signInAs, signOutAll } from './testing'

afterEach(signOutAll)

function item(over: Record<string, unknown> = {}) {
  return {
    roomId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
    code: 'PUB234',
    hostNickname: 'MusicLover',
    playerCount: 3,
    maxPlayers: 5,
    roundCount: 10,
    status: 'LOBBY',
    playlistName: 'V-Pop Hits 2026',
    createdAt: '2026-10-29T10:05:00Z',
    ...over,
  }
}

function listing(items: unknown[]) {
  server.use(
    http.get('*/api/v1/rooms/public', () =>
      HttpResponse.json({ page: 0, size: 50, totalItems: items.length, totalPages: 1, items }),
    ),
  )
}

describe('public room list (AC1)', () => {
  it('lists waiting rooms with music set and player count, and only those', async () => {
    signInAs('u-1')
    listing([
      item(),
      item({ roomId: 'r2', code: 'PLAY22', hostNickname: 'Busy', status: 'PLAYING' }),
    ])
    renderPublic('/rooms')

    expect(await screen.findByText('V-Pop Hits 2026')).toBeInTheDocument()
    expect(screen.getByText('3/5')).toBeInTheDocument()
    expect(screen.queryByText('Busy')).not.toBeInTheDocument()
    expect(screen.queryByText(/Chủ phòng Busy/)).not.toBeInTheDocument()
  })

  it('joins a room through its code and lands on the lobby route', async () => {
    signInAs('u-1')
    listing([item()])
    renderPublic('/rooms')
    await userEvent.click(await screen.findByRole('link', { name: 'Vào phòng của MusicLover' }))
    expect(await screen.findByTestId('where')).toHaveTextContent('lobby:/r/PUB234')
  })

  it('does not let anyone join a full room', async () => {
    listing([item({ playerCount: 5 })])
    renderPublic('/rooms')
    expect(await screen.findByText('Đầy')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Vào phòng của/ })).not.toBeInTheDocument()
  })

  it('says so when no room is waiting', async () => {
    listing([])
    renderPublic('/rooms')
    expect(await screen.findByText('Chưa có phòng nào đang chờ')).toBeInTheDocument()
  })

  it('shows an error with retry when the list cannot load', async () => {
    server.use(http.get('*/api/v1/rooms/public', () => new HttpResponse(null, { status: 500 })))
    renderPublic('/rooms')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    listing([item()])
    await userEvent.click(screen.getByRole('button', { name: /Thử lại/ }))
    await waitFor(() => {
      expect(screen.getByText('V-Pop Hits 2026')).toBeInTheDocument()
    })
  })

  it('offers the quick match entry', async () => {
    listing([])
    renderPublic('/rooms')
    expect(await screen.findByRole('link', { name: 'Ghép trận nhanh' })).toHaveAttribute(
      'href',
      '/quick-match',
    )
  })
})
