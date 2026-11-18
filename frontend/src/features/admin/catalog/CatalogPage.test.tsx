import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import tracksExample from '../../../../../contracts/examples/admin/list-admin-tracks.200.json'
import { server } from '@/mocks/server'
import { renderAdmin } from '../shell/testing'
import { CatalogPage } from './CatalogPage'
import { EMPTY, toFilters } from './filters'
import { adminCatalogHandlers } from './mocks'

beforeEach(() => {
  server.use(...adminCatalogHandlers)
})

describe('CatalogPage', () => {
  it('lists tracks in a table', async () => {
    renderAdmin(<CatalogPage />)
    const table = await screen.findByRole('table', { name: 'Danh sách bài trong kho nhạc' })
    expect(within(table).getByText('Nơi Này Có Anh')).toBeInTheDocument()
    expect(within(table).getByText('Đang bật')).toBeInTheDocument()
  })

  it('sends search and filters to the server', async () => {
    const asked: URLSearchParams[] = []
    server.use(
      http.get('*/api/v1/admin/tracks', ({ request }) => {
        asked.push(new URL(request.url).searchParams)
        return HttpResponse.json(tracksExample)
      }),
    )
    renderAdmin(<CatalogPage />)
    await screen.findByRole('table')
    await userEvent.type(screen.getByLabelText('Tên bài hoặc ca sĩ'), 'son tung')
    await userEvent.selectOptions(screen.getByLabelText('Trạng thái'), 'disabled')
    await userEvent.type(screen.getByLabelText('Quốc gia (VN, US…)'), 'vn')
    await userEvent.selectOptions(screen.getByLabelText('Thập niên'), '2010')
    await userEvent.click(screen.getByRole('button', { name: 'Lọc' }))
    await screen.findByRole('table')
    const last = asked.at(-1)
    expect(last?.get('q')).toBe('son tung')
    expect(last?.get('enabled')).toBe('false')
    expect(last?.get('countryCode')).toBe('VN')
    expect(last?.get('decade')).toBe('2010')
    expect(last?.get('page')).toBe('0')
  })

  it('disables a track with a reason, then can enable it again', async () => {
    const bodies: unknown[] = []
    let enabled = true
    server.use(
      http.get('*/api/v1/admin/tracks', () =>
        HttpResponse.json({
          ...tracksExample,
          items: [{ ...tracksExample.items[0], enabled }],
        }),
      ),
      http.put('*/api/v1/admin/tracks/:id/status', async ({ request }) => {
        const body = (await request.json()) as { enabled: boolean }
        bodies.push(body)
        enabled = body.enabled
        return HttpResponse.json({ ...tracksExample.items[0], enabled })
      }),
    )
    renderAdmin(<CatalogPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Tắt bài Nơi Này Có Anh' }))
    await userEvent.type(screen.getByLabelText('Lý do (không bắt buộc)'), 'Bản quyền')
    await userEvent.click(screen.getByRole('button', { name: 'Tắt bài' }))
    expect(await screen.findByText('Đã tắt Nơi Này Có Anh')).toBeInTheDocument()
    expect(bodies[0]).toEqual({ enabled: false, disabledReason: 'Bản quyền' })
    await userEvent.click(await screen.findByRole('button', { name: 'Bật bài Nơi Này Có Anh' }))
    expect(await screen.findByText('Đã bật Nơi Này Có Anh')).toBeInTheDocument()
    expect(bodies[1]).toEqual({ enabled: true, disabledReason: null })
  })

  it('imports tracks from the provider and shows the summary', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/admin/tracks/import', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          totalProcessed: 10,
          importedCount: 8,
          skippedNoPreviewCount: 1,
          duplicateCount: 1,
          tracks: [],
        })
      }),
    )
    renderAdmin(<CatalogPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Nhập bài' }))
    await userEvent.type(screen.getByLabelText('Từ khóa'), 'den vau')
    await userEvent.clear(screen.getByLabelText('Tối đa'))
    await userEvent.type(screen.getByLabelText('Tối đa'), '10')
    await userEvent.type(screen.getByLabelText('Quốc gia mặc định'), 'vn')
    await userEvent.click(screen.getByRole('button', { name: 'Nhập' }))
    expect(await screen.findByText('Đã nhập 8/10 bài')).toBeInTheDocument()
    expect(body).toEqual({ query: 'den vau', limit: 10, defaultCountryCode: 'VN' })
  })

  it('shows an empty state when nothing matches', async () => {
    server.use(
      http.get('*/api/v1/admin/tracks', () =>
        HttpResponse.json({ ...tracksExample, items: [], totalItems: 0, totalPages: 0 }),
      ),
    )
    renderAdmin(<CatalogPage />)
    expect(await screen.findByText('Không có bài nào')).toBeInTheDocument()
  })

  it('drops blank and malformed filters', () => {
    expect(toFilters({ ...EMPTY, q: '  ', countryCode: 'v' }, 2)).toEqual({
      q: undefined,
      enabled: undefined,
      genreCode: undefined,
      countryCode: undefined,
      decade: undefined,
      page: 2,
    })
  })
})
