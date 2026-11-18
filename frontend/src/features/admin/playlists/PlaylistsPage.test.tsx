import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import example from '../../../../../contracts/examples/admin/create-admin-playlist.201.json'
import tracksExample from '../../../../../contracts/examples/admin/list-admin-tracks.200.json'
import { server } from '@/mocks/server'
import { renderAdmin } from '../shell/testing'
import { PlaylistsPage } from './PlaylistsPage'
import { adminPlaylistHandlers } from './mocks'

const trackId = (i: number) => `22222222-2222-4222-8222-22222222220${String(i)}`
const tracks = Array.from({ length: 6 }, (_, i) => ({
  ...tracksExample.items[0],
  id: trackId(i + 1),
  title: `Bài hát ${String(i + 1)}`,
  enabled: true,
}))
beforeEach(() => {
  server.use(...adminPlaylistHandlers)
  server.use(
    http.get('*/api/v1/admin/playlists', () => HttpResponse.json({ items: [example] })),
    http.get('*/api/v1/admin/tracks', () =>
      HttpResponse.json({ items: tracks, page: 0, size: 20, totalPages: 1, totalItems: 6 }),
    ),
  )
})
async function searchTracks() {
  await userEvent.type(screen.getByRole('searchbox'), 'Bài')
  await userEvent.click(screen.getByRole('button', { name: 'Tìm' }))
  await screen.findByRole('button', { name: 'Thêm Bài hát 6' })
}
async function edit() {
  await userEvent.click(await screen.findByRole('button', { name: /Sửa bộ đề Indie/ }))
}

describe('PlaylistsPage', () => {
  it('creates a playlist with five selected tracks and prevents duplicate selections', async () => {
    let created: unknown
    server.use(
      http.post('*/api/v1/admin/playlists', async ({ request }) => {
        created = await request.json()
        return HttpResponse.json(example, { status: 201 })
      }),
    )
    renderAdmin(<PlaylistsPage />)
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bộ đề' }))
    await userEvent.type(screen.getByLabelText('Mã'), 'indie-viet-2020s')
    await userEvent.type(screen.getByLabelText('Tên tiếng Việt'), 'Indie Việt')
    await userEvent.type(screen.getByLabelText('Tên tiếng Anh'), 'Vietnamese Indie')
    await searchTracks()
    for (let i = 1; i <= 5; i++)
      await userEvent.click(screen.getByRole('button', { name: `Thêm Bài hát ${String(i)}` }))
    expect(screen.getByRole('button', { name: 'Thêm Bài hát 1' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByText('Đã lưu bộ đề')).toBeInTheDocument()
    expect(created).toEqual({
      code: 'indie-viet-2020s',
      nameVi: 'Indie Việt',
      nameEn: 'Vietnamese Indie',
      trackIds: tracks.slice(0, 5).map((track) => track.id),
    })
  })
  it('validates code, names and the minimum before creating', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/admin/playlists', () => {
        calls++
        return HttpResponse.json(example)
      }),
    )
    renderAdmin(<PlaylistsPage />)
    await userEvent.click(screen.getByRole('button', { name: 'Tạo bộ đề' }))
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(screen.getByLabelText('Mã')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Tên tiếng Việt')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('alert')).toHaveTextContent('Cần ít nhất 5 bài.')
    expect(calls).toBe(0)
  })
  it('adds before removing so an enabled playlist keeps five tracks, then updates names', async () => {
    const calls: { method: string; body: unknown }[] = []
    server.use(
      ...['post', 'delete', 'put'].map((method) => {
        const handler = method === 'post' ? http.post : method === 'delete' ? http.delete : http.put
        return handler(
          method === 'put' ? '*/api/v1/admin/playlists/:id' : '*/api/v1/admin/playlists/:id/tracks',
          async ({ request }) => {
            calls.push({ method, body: await request.json() })
            return HttpResponse.json(example)
          },
        )
      }),
    )
    renderAdmin(<PlaylistsPage />)
    await edit()
    await searchTracks()
    await userEvent.click(screen.getByRole('button', { name: 'Thêm Bài hát 6' }))
    await userEvent.click(screen.getByRole('button', { name: `Bỏ Bài #${trackId(1)} khỏi bộ đề` }))
    await userEvent.clear(screen.getByLabelText('Tên tiếng Việt'))
    await userEvent.type(screen.getByLabelText('Tên tiếng Việt'), 'Bộ mới')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    await screen.findByText('Đã lưu bộ đề')
    expect(calls).toEqual([
      { method: 'post', body: { trackIds: [trackId(6)] } },
      { method: 'delete', body: { trackIds: [trackId(1)] } },
      { method: 'put', body: { nameVi: 'Bộ mới', nameEn: example.nameEn, enabled: true } },
    ])
  })
  it('disables before removing and prevents leaving or editing during save', async () => {
    const calls: string[] = []
    server.use(
      http.put('*/api/v1/admin/playlists/:id', async () => {
        calls.push('disable')
        await delay(150)
        return HttpResponse.json({ ...example, enabled: false })
      }),
      http.delete('*/api/v1/admin/playlists/:id/tracks', () => {
        calls.push('remove')
        return HttpResponse.json({ ...example, enabled: false, trackIds: [] })
      }),
    )
    renderAdmin(<PlaylistsPage />)
    await edit()
    await userEvent.click(screen.getByLabelText('Bật bộ đề'))
    await userEvent.click(screen.getByRole('button', { name: `Bỏ Bài #${trackId(1)} khỏi bộ đề` }))
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(screen.getByRole('button', { name: 'Về danh sách' })).toBeDisabled()
    expect(screen.getByLabelText('Tên tiếng Việt')).toBeDisabled()
    await screen.findByText('Đã lưu bộ đề')
    expect(calls).toEqual(['disable', 'remove'])
  })
  it('keeps the draft and warns about partial save when a later operation fails', async () => {
    let additions = 0
    server.use(
      http.post('*/api/v1/admin/playlists/:id/tracks', () => {
        additions++
        return HttpResponse.json(example)
      }),
      http.delete('*/api/v1/admin/playlists/:id/tracks', () =>
        HttpResponse.json({ code: 'NOT_ENOUGH_TRACKS' }, { status: 422 }),
      ),
    )
    renderAdmin(<PlaylistsPage />)
    await edit()
    await searchTracks()
    await userEvent.click(screen.getByRole('button', { name: 'Thêm Bài hát 6' }))
    await userEvent.click(screen.getByRole('button', { name: `Bỏ Bài #${trackId(1)} khỏi bộ đề` }))
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Bộ đề đang bật cần ít nhất 5 bài đang bật.')
    expect(alert).toHaveTextContent('Một số thay đổi có thể đã được lưu.')
    expect(screen.getByLabelText('Tên tiếng Việt')).toHaveValue(example.nameVi)
    expect(additions).toBe(1)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Lưu' })).toBeEnabled())
  })
  it('shows standard playlists as read-only and handles an empty list', async () => {
    server.use(
      http.get('*/api/v1/admin/playlists', () =>
        HttpResponse.json({ items: [{ ...example, curated: false }] }),
      ),
    )
    const view = renderAdmin(<PlaylistsPage />)
    expect(await screen.findByRole('button', { name: /Sửa bộ đề/ })).toBeDisabled()
    view.unmount()
    server.use(http.get('*/api/v1/admin/playlists', () => HttpResponse.json({ items: [] })))
    renderAdmin(<PlaylistsPage />)
    expect(await screen.findByText('Chưa có bộ đề')).toBeInTheDocument()
  })
})
