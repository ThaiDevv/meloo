import { expect, test } from '@playwright/test'
import session from '../../../../../contracts/examples/identity/create-guest.201.json' with { type: 'json' }
import me from '../../../../../contracts/examples/identity/get-me.registered.200.json' with { type: 'json' }
import user from '../../../../../contracts/examples/admin/get-admin-user.200.json' with { type: 'json' }
import users from '../../../../../contracts/examples/admin/list-admin-users.200.json' with { type: 'json' }
import reports from '../../../../../contracts/examples/admin/list-admin-reports.200.json' with { type: 'json' }
import words from '../../../../../contracts/examples/admin/list-admin-banned-words.200.json' with { type: 'json' }
import schedule from '../../../../../contracts/examples/admin/list-admin-daily-schedule.200.json' with { type: 'json' }
import tracks from '../../../../../contracts/examples/admin/list-admin-tracks.200.json' with { type: 'json' }
import audit from '../../../../../contracts/examples/admin/list-admin-audit-logs.200.json' with { type: 'json' }

const wordExample = words.items[0]
if (!wordExample) throw new Error('Missing contract word example')

// Contract fixtures isolate browser behavior; the real admin endpoints are verified in MEL-87.
test('users, moderation and audit work with confirmations, keyboard, mobile and English', async ({
  page,
}) => {
  let account = { ...user }
  let reportItems = reports.items
  let wordItems = words.items
  let scheduleItems = schedule.items.map((item) => ({
    ...item,
    challengeDate: item.locked ? item.challengeDate : '2099-01-02',
  }))
  const writes: { path: string; body: unknown }[] = []
  const errors: string[] = []
  let auditQuery = ''
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route('https://fonts.googleapis.com/**', (route) =>
    route.fulfill({ contentType: 'text/css', body: '' }),
  )
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname,
      method = request.method()
    const json = (body: unknown, status = 200) => route.fulfill({ json: body, status })
    if (path === '/api/v1/auth/refresh') return json({ ...session, user: { ...me, role: 'ADMIN' } })
    if (method !== 'GET')
      writes.push({ path, body: request.postData() ? request.postDataJSON() : null })
    if (path === '/api/v1/admin/users') return json({ ...users, items: [account] })
    if (path === `/api/v1/admin/users/${user.id}`) return json(account)
    if (path.endsWith('/lock')) {
      account = { ...account, status: 'LOCKED' }
      return json(account)
    }
    if (path.endsWith('/role')) {
      account = { ...account, role: 'ADMIN' }
      return json(account)
    }
    if (path === '/api/v1/admin/reports') return json({ ...reports, items: reportItems })
    if (path.endsWith('/resolve')) {
      reportItems = []
      return json({ ...reports.items[0], status: 'ACTIONED' })
    }
    if (path === '/api/v1/admin/banned-words' && method === 'GET') return json({ items: wordItems })
    if (path === '/api/v1/admin/banned-words' && method === 'POST') {
      const word = {
        ...wordExample,
        id: '66666666-6666-4666-8666-666666666602',
        word: 'browser-spam',
      }
      wordItems = [...wordItems, word]
      return json(word, 201)
    }
    if (path === '/api/v1/admin/daily-schedule') return json({ items: scheduleItems })
    if (path.startsWith('/api/v1/admin/daily-schedule/')) {
      const first = schedule.items[0]
      if (!first) throw new Error('Missing schedule fixture')
      const saved = { ...first, challengeDate: path.split('/').at(-1) ?? '', locked: false }
      scheduleItems = [
        ...scheduleItems.filter((item) => item.challengeDate !== saved.challengeDate),
        saved,
      ]
      return json(saved)
    }
    if (path === '/api/v1/admin/tracks') return json(tracks)
    if (path === '/api/v1/admin/audit-logs') {
      auditQuery = url.search
      return json(audit)
    }
    return json({ code: 'NOT_FOUND' }, 404)
  })
  const noOverflow = async () => {
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/admin/users')
  await page.getByRole('searchbox').fill('Minh')
  await page.getByRole('button', { name: 'Lọc', exact: true }).click()
  await page.getByRole('button', { name: 'Xem MinhNhac' }).click()
  await expect(page.getByText(user.id, { exact: true })).toBeVisible()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await page.screenshot({
    path: 'src/features/admin/moderation/evidence/users-desktop.png',
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: 'Khóa tài khoản', exact: true }).click()
  let dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: 'Xác nhận' })).toBeDisabled()
  await dialog.getByLabel('Lý do khóa').fill('Browser spam')
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: 'Khóa tài khoản', exact: true }).click()
  await dialog.getByLabel('Lý do khóa').fill('Browser spam')
  await page.setViewportSize({ width: 360, height: 800 })
  await noOverflow()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await page.screenshot({
    path: 'src/features/admin/moderation/evidence/lock-mobile.png',
    fullPage: false,
    animations: 'disabled',
  })
  await dialog.getByRole('button', { name: 'Xác nhận' }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes[0]?.body).toEqual({ locked: true, reason: 'Browser spam' })
  await page.getByRole('button', { name: 'Đổi vai trò' }).click()
  await dialog.getByRole('button', { name: 'Xác nhận' }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes[1]?.body).toEqual({ role: 'ADMIN' })
  await page.getByRole('link', { name: 'Kiểm duyệt', exact: true }).click()
  await page.getByText('Ngữ cảnh (2 tin nhắn)').click()
  await expect(page.getByText('Chào mọi người')).toBeVisible()
  await noOverflow()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await page.screenshot({
    path: 'src/features/admin/moderation/evidence/reports-mobile.png',
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: 'Xử lý báo cáo', exact: true }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByRole('checkbox').check()
  await dialog.getByLabel('Lý do khóa').fill('Repeated spam')
  await dialog.getByRole('button', { name: 'Xác nhận' }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByText('Không có kết quả')).toBeVisible()
  await page.getByRole('button', { name: 'Từ cấm', exact: true }).click()
  await page.getByRole('button', { name: 'Thêm từ cấm' }).click()
  await dialog.getByLabel('Từ hoặc cụm từ').fill('browser-spam')
  await dialog.getByRole('button', { name: 'Xác nhận' }).click()
  await expect(page.getByText('browser-spam', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Lịch thử thách', exact: true }).click()
  await page.getByLabel('Ngày thử thách (Việt Nam)').fill('2099-01-01')
  await page.getByRole('searchbox').fill('Nơi')
  await page.getByRole('button', { name: 'Tìm', exact: true }).click()
  await page.getByRole('button', { name: 'Chọn Nơi Này Có Anh', exact: true }).click()
  await page.getByRole('button', { name: 'Lên lịch bài', exact: true }).click()
  await expect(dialog).toContainText('2099-01-01')
  await dialog.getByRole('button', { name: 'Xác nhận' }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.locator('time[datetime="2099-01-01"]')).toBeVisible()
  await noOverflow()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await page.screenshot({
    path: 'src/features/admin/moderation/evidence/schedule-mobile.png',
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('link', { name: 'Nhật ký', exact: true }).click()
  await page.getByLabel('ID quản trị viên (UUID)').fill(audit.items[0]?.actorId ?? '')
  await page.getByLabel('Mã thao tác').fill('USER_LOCKED')
  await page.getByLabel('Từ lúc (giờ địa phương)').fill('2026-10-28T10:00')
  await page.getByLabel('Đến lúc (giờ địa phương)').fill('2026-10-30T10:00')
  await page.getByRole('button', { name: 'Lọc', exact: true }).click()
  await expect.poll(() => auditQuery).toContain('action=USER_LOCKED')
  await expect.poll(() => auditQuery).toContain('from=')
  await page.getByText('Chi tiết · #1').click()
  await noOverflow()
  await page.getByRole('button', { name: 'EN', exact: true }).click()
  await page.setViewportSize({ width: 768, height: 1000 })
  await expect(page.getByText('By AdminThai', { exact: true })).toBeVisible()
  await page.evaluate(() => {
    window.scrollTo(0, 0)
  })
  await page.screenshot({
    path: 'src/features/admin/moderation/evidence/audit-english-tablet.png',
    fullPage: true,
    animations: 'disabled',
  })
  expect(errors).toEqual([])
})
for (const route of ['users', 'moderation', 'audit']) {
  test(`restored non-admin cannot access /admin/${route}`, async ({ page }) => {
    const adminRequests: string[] = []
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/admin/')) adminRequests.push(request.url())
    })
    await page.route('**/api/v1/auth/refresh', (request) =>
      request.fulfill({ json: { ...session, user: me } }),
    )
    await page.goto(`/admin/${route}`)
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('navigation', { name: 'Menu quản trị' })).toHaveCount(0)
    expect(adminRequests).toEqual([])
  })
}
