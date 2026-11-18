import { expect, test } from '@playwright/test'
import type { components } from '@/shared/api/schema.gen'
import session from '../../../../../contracts/examples/identity/create-guest.201.json' with { type: 'json' }
import user from '../../../../../contracts/examples/identity/get-me.registered.200.json' with { type: 'json' }
import dashboard from '../../../../../contracts/examples/admin/get-admin-dashboard.200.json' with { type: 'json' }
import analytics from '../../../../../contracts/examples/admin/get-admin-analytics.200.json' with { type: 'json' }
import catalog from '../../../../../contracts/examples/admin/list-admin-tracks.200.json' with { type: 'json' }
import playlist from '../../../../../contracts/examples/admin/create-admin-playlist.201.json' with { type: 'json' }

const trackExample = catalog.items[0]
if (!trackExample) throw new Error('Missing contract track example')

type Track = components['schemas']['AdminTrack']
type Playlist = components['schemas']['AdminPlaylist']
const trackId = (i: number) => `22222222-2222-4222-8222-22222222220${String(i)}`

// Browser smoke tests isolate the UI with contract fixtures; backend security/SQL have integration tests.
test('admin dashboard, catalog mutations and playlist create/edit work at desktop and mobile sizes', async ({
  page,
}) => {
  // Keep screenshot fixtures independent of external font service availability.
  await page.route('https://fonts.googleapis.com/**', (route) =>
    route.fulfill({ contentType: 'text/css', body: '' }),
  )
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const tracks: Track[] = Array.from({ length: 6 }, (_, i) => ({
    ...trackExample,
    provider: 'DEEZER',
    disabledReason: i === 5 ? 'Preview lỗi' : null,
    enabled: i !== 5,
    id: trackId(i + 1),
    title: `${trackExample.title} ${String(i + 1)}`,
  }))
  let playlists: Playlist[] = [
    { ...playlist, trackIds: tracks.map((track) => track.id), enabledTrackCount: 5 },
  ]
  let removedTracks: unknown
  let lastQuery = ''
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    const json = (body: unknown, status = 200) => route.fulfill({ json: body, status })
    if (path === '/api/v1/auth/refresh')
      return json({ ...session, user: { ...user, role: 'ADMIN' } })
    if (path === '/api/v1/admin/dashboard')
      return json({ ...dashboard, activeRooms: 0, onlinePlayers: 0 })
    if (path === '/api/v1/admin/analytics') {
      // Mount Recharts after web fonts settle: its text measurement cache otherwise depends on timing.
      await page.evaluate(() => document.fonts.ready.then(() => true))
      const days = Number(url.searchParams.get('days') ?? 30)
      return json({ ...analytics, daily: analytics.daily.slice(-days) })
    }
    if (path === '/api/v1/admin/tracks' && method === 'GET') {
      lastQuery = url.search
      const q = url.searchParams.get('q')?.toLowerCase() ?? ''
      const enabled = url.searchParams.get('enabled')
      const items = tracks.filter(
        (track) =>
          track.title.toLowerCase().includes(q) &&
          (enabled === null || track.enabled === (enabled === 'true')),
      )
      return json({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1 })
    }
    if (path.endsWith('/status')) {
      const body = request.postDataJSON() as { enabled: boolean; disabledReason?: string }
      const track = tracks.find((track) => path.includes(track.id))
      if (!track) return json({}, 404)
      track.enabled = body.enabled
      track.disabledReason = body.disabledReason ?? null
      return json(track)
    }
    if (path.endsWith('/tracks/import'))
      return json({
        totalProcessed: 6,
        importedCount: 6,
        duplicateCount: 0,
        skippedNoPreviewCount: 0,
        tracks,
      })
    if (path === '/api/v1/admin/playlists' && method === 'GET') return json({ items: playlists })
    if (path === '/api/v1/admin/playlists' && method === 'POST') {
      const body = request.postDataJSON() as {
        code: string
        nameVi: string
        nameEn: string
        trackIds: string[]
      }
      const saved = { ...playlist, ...body, id: '11111111-1111-4111-8111-111111111110' }
      playlists = [...playlists, saved]
      return json(saved, 201)
    }
    if (
      path.startsWith('/api/v1/admin/playlists/') &&
      path.endsWith('/tracks') &&
      method === 'DELETE'
    ) {
      const body = request.postDataJSON() as { trackIds: string[] }
      removedTracks = body.trackIds
      const saved = playlists.find((item) => path.includes(item.id))
      if (!saved) return json({}, 404)
      saved.trackIds = saved.trackIds.filter((id) => !body.trackIds.includes(id))
      return json(saved)
    }
    if (path.startsWith('/api/v1/admin/playlists/') && method === 'PUT') {
      const body = request.postDataJSON() as { nameVi: string; nameEn: string; enabled: boolean }
      const saved = playlists.find((item) => path.endsWith(item.id))
      if (!saved) return json({}, 404)
      Object.assign(saved, body)
      return json(saved)
    }
    return json({ code: 'NOT_FOUND' }, 404)
  })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/admin')
  await expect(page.getByText('19/52 người chơi')).toBeVisible()
  await expect(page.getByRole('figure')).toHaveCount(3)
  for (const figure of await page.getByRole('figure').all()) {
    await expect(figure.locator('svg').first()).toBeVisible()
  }
  await page.getByRole('button', { name: '7 ngày' }).click()
  await expect(page.getByRole('button', { name: '7 ngày' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('region', { name: 'Theo ngày' })).toHaveAttribute(
    'aria-busy',
    'false',
  )
  await expect(page).toHaveScreenshot('dashboard-desktop.png', {
    fullPage: true,
    animations: 'disabled',
  })

  await page.getByRole('link', { name: 'Kho nhạc', exact: true }).click()
  await page.getByRole('searchbox', { name: 'Tên bài hoặc ca sĩ' }).fill('Nơi')
  await page.getByRole('button', { name: 'Lọc', exact: true }).click()
  await expect.poll(() => lastQuery).toContain('q=N')
  await page.getByRole('button', { name: 'Tắt bài Nơi Này Có Anh 1', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Tắt bài hát' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('textbox').fill('Preview lỗi')
  await dialog.getByRole('button', { name: 'Tắt bài', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await page.getByRole('button', { name: 'Bật bài Nơi Này Có Anh 1', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Tắt bài Nơi Này Có Anh 1', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Nhập bài', exact: true }).click()
  await page.getByLabel('Từ khóa', { exact: true }).fill('indie')
  await page.getByRole('button', { name: 'Nhập', exact: true }).click()
  await expect(page.getByText('Đã nhập 6/6 bài')).toBeVisible()
  await page.keyboard.press('Escape')
  while (await page.getByRole('button', { name: 'Ẩn thông báo' }).count()) {
    await page.getByRole('button', { name: 'Ẩn thông báo' }).first().click()
  }
  await page.setViewportSize({ width: 360, height: 800 })
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await expect(page).toHaveScreenshot('catalog-mobile.png', {
    fullPage: true,
    animations: 'disabled',
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )

  await page.getByRole('link', { name: 'Bộ đề', exact: true }).click()
  await page.getByRole('button', { name: /Sửa bộ đề Indie/ }).click()
  const removeDisabled = page.getByRole('button', {
    name: 'Bỏ Nơi Này Có Anh 6 · Sơn Tùng M-TP khỏi bộ đề',
    exact: true,
  })
  await expect(removeDisabled).toBeVisible()
  await expect(page.getByText('Sơn Tùng M-TP · Đang tắt', { exact: true })).toBeVisible()
  await expect(
    page.getByRole('button', {
      name: 'Bỏ Nơi Này Có Anh 1 · Sơn Tùng M-TP khỏi bộ đề',
      exact: true,
    }),
  ).toBeVisible()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await expect(page).toHaveScreenshot('playlist-existing-mobile.png', {
    fullPage: true,
    animations: 'disabled',
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await removeDisabled.click()
  await page.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Tạo bộ đề', exact: true })).toBeVisible()
  expect(removedTracks).toEqual([trackId(6)])
  while (await page.getByRole('button', { name: 'Ẩn thông báo' }).count()) {
    await page.getByRole('button', { name: 'Ẩn thông báo' }).first().click()
  }
  await page.getByRole('button', { name: 'Tạo bộ đề', exact: true }).click()
  await page.getByLabel('Mã', { exact: true }).fill('browser-set')
  await page.getByLabel('Tên tiếng Việt').fill('Bộ đề kiểm tra')
  await page.getByLabel('Tên tiếng Anh').fill('Browser test set')
  await page.getByRole('searchbox').fill('Nơi')
  await page.getByRole('button', { name: 'Tìm', exact: true }).click()
  for (let i = 1; i <= 5; i++)
    await page
      .getByRole('button', { name: `Thêm Nơi Này Có Anh ${String(i)}`, exact: true })
      .click()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await expect(page).toHaveScreenshot('playlist-editor-mobile.png', {
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Sửa bộ đề Bộ đề kiểm tra', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Sửa bộ đề Bộ đề kiểm tra', exact: true }).click()
  await page.getByLabel('Tên tiếng Việt').fill('Tên đã sửa')
  await page.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Sửa bộ đề Tên đã sửa', exact: true }),
  ).toBeVisible()
  while (await page.getByRole('button', { name: 'Ẩn thông báo' }).count()) {
    await page.getByRole('button', { name: 'Ẩn thông báo' }).first().click()
  }
  await page.getByRole('button', { name: 'EN', exact: true }).click()
  await page.setViewportSize({ width: 768, height: 1000 })
  await page.getByRole('link', { name: 'Overview', exact: true }).click()
  await expect(page.getByText('Players per day', { exact: true })).toBeVisible()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await expect(page).toHaveScreenshot('dashboard-english-tablet.png', {
    fullPage: true,
    animations: 'disabled',
  })
  expect(errors).toEqual([])
})

test('a restored user is redirected away from all admin pages', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({ json: { ...session, user } }),
  )
  await page.goto('/admin/catalog')
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('navigation', { name: 'Menu quản trị' })).toHaveCount(0)
})
