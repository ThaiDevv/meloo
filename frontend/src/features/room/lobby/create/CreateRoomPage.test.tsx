import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { HOST_ID, room } from '@/shared/realtime/testing/fixtures'
import { renderLobby, signIn } from '../testing/renderLobby'

const problem = { 'Content-Type': 'application/problem+json' }

/** Answers POST /rooms and returns the bodies it received. */
function captureCreate(respond: () => Response = () => HttpResponse.json(room(), { status: 201 })) {
  const bodies: unknown[] = []
  server.use(
    http.post('/api/v1/rooms', async ({ request }) => {
      bodies.push(await request.json())
      return respond()
    }),
  )
  return bodies
}

beforeEach(() => {
  signIn(HOST_ID, 'ThaiPO')
})

describe('CreateRoomPage', () => {
  it('creates a private room of 10 rounds and 5 players from the whole catalog by default', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderLobby('/rooms/new')

    expect(screen.getByRole('heading', { level: 1, name: 'Tạo phòng' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tạo phòng' }))

    await waitFor(() => {
      expect(bodies).toEqual([{ roomType: 'PRIVATE_ROOM', roundCount: 10, maxPlayers: 5 }])
    })
  })

  it('sends the visibility, the number of rounds and the number of players that were picked', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderLobby('/rooms/new')

    await user.click(screen.getByRole('radio', { name: /Phòng công khai/ }))
    const rounds = screen.getByRole('group', { name: 'Số vòng' })
    await user.click(within(rounds).getByRole('radio', { name: '15' }))
    const players = screen.getByRole('group', { name: 'Số người tối đa' })
    await user.click(within(players).getByRole('radio', { name: '3' }))
    await user.click(screen.getByRole('button', { name: 'Tạo phòng' }))

    await waitFor(() => {
      expect(bodies).toEqual([{ roomType: 'PUBLIC_ROOM', roundCount: 15, maxPlayers: 3 }])
    })
  })

  it('offers 5, 10 and 15 rounds and 2 to 5 players', () => {
    renderLobby('/rooms/new')
    const values = (name: string) =>
      within(screen.getByRole('group', { name }))
        .getAllByRole('radio')
        .map((r) => r.getAttribute('value'))
    expect(values('Số vòng')).toEqual(['5', '10', '15'])
    expect(values('Số người tối đa')).toEqual(['2', '3', '4', '5'])
  })

  it('sends a playlist alone, never together with a filter', async () => {
    const user = userEvent.setup()
    const bodies = captureCreate()
    renderLobby('/rooms/new')

    await user.click(screen.getByRole('tab', { name: 'Bộ đề' }))
    const create = screen.getByRole('button', { name: 'Tạo phòng' })
    expect(create).toBeDisabled()
    expect(screen.getByText('Hãy chọn một bộ đề để tạo phòng.')).toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: /V-pop thập niên 2010/ }))
    await user.click(screen.getByRole('button', { name: 'Tạo phòng' }))

    await waitFor(() => {
      expect(bodies).toHaveLength(1)
    })
    expect(bodies[0]).toEqual({
      roomType: 'PRIVATE_ROOM',
      roundCount: 10,
      maxPlayers: 5,
      playlistId: '14f8a2c6-d570-4e9b-b3a8-c6e1d0f27a95',
    })
  })

  it('opens the lobby of the new room with the room it just created', async () => {
    const user = userEvent.setup()
    captureCreate()
    let joins = 0
    server.use(
      http.post('/api/v1/rooms/join', () => {
        joins += 1
        return HttpResponse.json(room())
      }),
    )
    renderLobby('/rooms/new')

    await user.click(screen.getByRole('button', { name: 'Tạo phòng' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Sảnh chờ' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'M E L 8 8 9' })).toBeInTheDocument()
    expect(joins).toBe(0)
  })

  it.each([
    [
      'not enough tracks',
      422,
      { code: 'NOT_ENOUGH_TRACKS', status: 422, title: 't', type: 'about:blank', missingTracks: 3 },
      /thiếu 3 bài/,
    ],
    [
      'a playlist that is gone',
      404,
      { code: 'PLAYLIST_NOT_FOUND', status: 404, title: 't', type: 'about:blank' },
      /không còn khả dụng/,
    ],
    [
      'too many requests',
      429,
      { code: 'RATE_LIMITED', status: 429, title: 't', type: 'about:blank' },
      /thao tác quá nhanh/,
    ],
    [
      'a server error',
      500,
      { code: 'INTERNAL_ERROR', status: 500, title: 't', type: 'about:blank' },
      /Không tạo được phòng/,
    ],
  ])('says what went wrong on %s and keeps the form', async (_name, status, body, text) => {
    const user = userEvent.setup()
    captureCreate(() => HttpResponse.json(body, { status, headers: problem }))
    renderLobby('/rooms/new')

    await user.click(screen.getByRole('button', { name: 'Tạo phòng' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(text)
    expect(screen.getByRole('button', { name: 'Tạo phòng' })).toBeEnabled()
  })

  it('sends someone who is not signed in to /start', async () => {
    const user = userEvent.setup()
    captureCreate(() =>
      HttpResponse.json(
        { code: 'UNAUTHORIZED', status: 401, title: 't', type: 'about:blank' },
        { status: 401, headers: problem },
      ),
    )
    renderLobby('/rooms/new')

    await user.click(screen.getByRole('button', { name: 'Tạo phòng' }))

    expect(await screen.findByText('trang bắt đầu')).toBeInTheDocument()
  })
})

describe('JoinByCodeForm', () => {
  it('reads a code typed with spaces and opens its room', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('/api/v1/rooms/join', () =>
        HttpResponse.json(
          { code: 'ROOM_NOT_FOUND', status: 404, title: 't', type: 'about:blank' },
          { status: 404, headers: problem },
        ),
      ),
    )
    renderLobby('/rooms/new')

    const input = screen.getByLabelText('Đã có mã phòng?')
    await user.type(input, 'mel 889')
    await user.click(screen.getByRole('button', { name: 'Vào phòng' }))

    expect(await screen.findByRole('heading', { name: 'Không tìm thấy phòng' })).toBeInTheDocument()
  })

  it('refuses a code that cannot be one, without leaving the page', async () => {
    const user = userEvent.setup()
    renderLobby('/rooms/new')

    const input = screen.getByLabelText('Đã có mã phòng?')
    await user.type(input, 'ABC')
    await user.click(screen.getByRole('button', { name: 'Vào phòng' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Mã phòng gồm 6 ký tự')
    expect(input).toBeInvalid()
    await user.type(input, 'DEF')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
