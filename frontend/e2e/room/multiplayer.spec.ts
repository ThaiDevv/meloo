import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'

const PLAYERS = {
  a: 'RoomHost61',
  b: 'RoomPlayerB61',
  c: 'RoomPlayerC61',
  d: 'RoomPlayerD61',
} as const

const LOBBY_HEADING = /Sảnh chờ|Lobby/i
const CORRECT_TITLE = 'Chúng Ta Của Hiện Tại'

async function createGuest(browser: Browser, nickname: string) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto('/start')
  const nicknameInput = page.locator('#guest-nickname')
  await expect(nicknameInput).toBeVisible({ timeout: 10_000 })
  await nicknameInput.fill(nickname)
  await page.locator('input[type="radio"]').first().check({ force: true })
  await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes('/api/v1/auth/guest') && response.ok(),
    ),
    page.getByRole('button', { name: /Vào chơi|Start playing|Tiếp tục/i }).click(),
  ])
  await expect(page).not.toHaveURL(/\/start(?:[?#]|$)/)
  return { context, page }
}

async function createRoom(page: Page) {
  await page.goto('/rooms/new')
  await page.locator('input[name="rounds"][value="5"]').check({ force: true })
  await page.locator('input[name="players"][value="5"]').check({ force: true })
  await page.getByRole('tab', { name: /Bộ đề|Playlists|By playlist/i }).click()
  const playlist = page.getByRole('button', { name: /Bộ chuẩn Meloo|Meloo Standard Set/i })
  await expect(playlist).toBeVisible({ timeout: 10_000 })
  await playlist.click()
  await page.getByRole('button', { name: /Tạo phòng|Create room/i, exact: true }).click()
  await expect(page.getByRole('heading', { name: LOBBY_HEADING })).toBeVisible({ timeout: 10_000 })

  const match = new URL(page.url()).pathname.match(/^\/r\/([A-Z0-9]{6})$/)
  const code = match?.[1]
  expect(code, 'created room URL contains its six-character code').toBeDefined()
  if (!code) throw new Error('Created room URL did not contain a six-character code')
  return code
}

async function joinWithCode(page: Page, code: string) {
  await page.goto('/rooms/new')
  await page.getByLabel(/Đã có mã phòng|Have a room code/i).fill(code)
  await page.getByRole('button', { name: /Vào phòng|Join/i, exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/r/${code}$`))
  await expect(page.getByRole('heading', { name: LOBBY_HEADING })).toBeVisible({ timeout: 10_000 })
}

async function sendChat(page: Page, message: string) {
  const input = page.getByRole('textbox', { name: /Tin nhắn|Message/i, exact: true })
  await expect(input).toBeEnabled({ timeout: 10_000 })
  await input.fill(message)
  await page.getByRole('button', { name: /Gửi|Send/i, exact: true }).click()
}

async function ready(page: Page) {
  const button = page.getByRole('button', { name: /Sẵn sàng|Ready/i, exact: true })
  await expect(button).toBeEnabled({ timeout: 10_000 })
  await button.click()
  await expect(button).toHaveAttribute('aria-pressed', 'true')
}

async function waitForRound(page: Page, round: number) {
  await expect(
    page.getByText(new RegExp(`(?:Vòng|Round) ${String(round)} \\/ 5`, 'i')),
  ).toBeVisible({
    timeout: 15_000,
  })
}

async function capturePodium(page: Page) {
  const podium = page.getByRole('heading', { name: /Bục vinh danh|Podium/i }).locator('xpath=..')
  let rows: string[] = []
  await expect
    .poll(
      async () => {
        rows = await podium.getByRole('listitem').allTextContents()
        return rows.length
      },
      { timeout: 15_000, intervals: [50] },
    )
    .toBeGreaterThanOrEqual(3)
  return rows
}

test.describe('Multiplayer room flow (MEL-61 / QA-02)', () => {
  test.setTimeout(90_000)

  test('four independent players complete a match, chat safely, and rematch with a late joiner', async ({
    browser,
  }, testInfo) => {
    const contexts: BrowserContext[] = []
    try {
      const [a, b, c, d] = await Promise.all([
        createGuest(browser, PLAYERS.a),
        createGuest(browser, PLAYERS.b),
        createGuest(browser, PLAYERS.c),
        createGuest(browser, PLAYERS.d),
      ])
      contexts.push(a.context, b.context, c.context, d.context)

      const code = await createRoom(a.page)
      await Promise.all([joinWithCode(b.page, code), joinWithCode(c.page, code)])

      for (const page of [a.page, b.page, c.page]) {
        await expect(page.getByText(PLAYERS.a, { exact: true })).toBeVisible()
        await expect(page.getByText(PLAYERS.b, { exact: true })).toBeVisible()
        await expect(page.getByText(PLAYERS.c, { exact: true })).toBeVisible()
      }

      await Promise.all([ready(b.page), ready(c.page)])

      const normalMessage = 'Chào cả phòng từ MEL-61'
      await sendChat(a.page, normalMessage)
      for (const page of [a.page, b.page, c.page]) {
        const log = page.getByRole('log', { name: /Tin nhắn trong phòng|Room messages/i })
        await expect(log).toContainText(normalMessage)
        await expect(log).toContainText(PLAYERS.a)
      }

      const start = a.page.getByRole('button', { name: /Bắt đầu|Start/i, exact: true })
      await expect(start).toBeEnabled({ timeout: 10_000 })
      await start.click()
      await Promise.all(
        [a.page, b.page, c.page].map((page) =>
          expect(page).toHaveURL(new RegExp(`/r/${code}/play$`), { timeout: 10_000 }),
        ),
      )

      // E2E rounds are intentionally only two seconds long. Register assertions for the brief
      // round/reveal states before interacting so Playwright observes them instead of polling late.
      const round2 = Promise.all([a.page, b.page, c.page].map((page) => waitForRound(page, 2)))
      const cTimedOut = expect
        .poll(
          () =>
            c.page
              .getByRole('paragraph')
              .filter({ hasText: /Hết giờ|Time is up/i })
              .count(),
          {
            timeout: 10_000,
            intervals: [25],
          },
        )
        .toBeGreaterThan(0)

      const guess = b.page.getByRole('combobox', { name: /Đoán tên bài|Guess the song/i })
      await guess.fill(CORRECT_TITLE)
      await b.page
        .getByRole('option', { name: new RegExp(CORRECT_TITLE, 'i') })
        .first()
        .click()
      await Promise.all([
        sendChat(a.page, CORRECT_TITLE),
        b.page.getByRole('button', { name: /Đoán|Guess/i, exact: true }).click(),
      ])
      await expect(
        b.page.getByRole('heading', { name: /Bạn đúng ở 2s|You got it at 2s/i }),
      ).toBeVisible()
      await expect(
        a.page.getByRole('alert').filter({
          hasText: /chứa đáp án|contains the current .*answer/i,
        }),
      ).toBeVisible()
      for (const page of [a.page, b.page, c.page]) {
        await expect(
          page.getByRole('log', { name: /Tin nhắn trong phòng|Room messages/i }),
        ).not.toContainText(CORRECT_TITLE)
      }

      await cTimedOut
      await round2
      await waitForRound(a.page, 3)
      const liveScore = a.page.getByRole('listitem', { name: PLAYERS.b })
      await expect(liveScore).toContainText(/1[.,]\d{3}/)

      const inviteOpenedAt = performance.now()
      await d.page.goto(`/r/${code}`)
      await expect(d.page.getByRole('heading', { name: LOBBY_HEADING })).toBeVisible({
        timeout: 3_000,
      })
      const inviteLatencyMs = Math.round(performance.now() - inviteOpenedAt)
      await testInfo.attach('NFR-11 invite-link latency', {
        body: JSON.stringify({ inviteLatencyMs, thresholdMs: 3_000 }, null, 2),
        contentType: 'application/json',
      })
      testInfo.annotations.push({
        type: 'NFR-11',
        description: `Invite link reached the lobby in ${String(inviteLatencyMs)} ms`,
      })
      expect(inviteLatencyMs).toBeLessThanOrEqual(3_000)
      await expect(d.page.getByText(/Trận đang diễn ra|match is in progress/i)).toBeVisible()

      const podiumRowsPromise = capturePodium(a.page)
      await waitForRound(a.page, 4)
      await waitForRound(a.page, 5)

      const podiumRows = await podiumRowsPromise
      const winner = podiumRows.find((row) => row.includes(PLAYERS.b))
      const tiedRows = podiumRows.filter(
        (row) => row.includes(PLAYERS.a) || row.includes(PLAYERS.c),
      )
      expect(winner).toMatch(/1[\s\S]*1[.,]\d{3}|1[.,]\d{3}[\s\S]*1/)
      expect(tiedRows).toHaveLength(2)
      for (const row of tiedRows) expect(row).toMatch(/2[\s\S]*0|0[\s\S]*2/)
      expect(podiumRows.some((row) => row.includes(PLAYERS.d))).toBe(false)

      for (const page of [a.page, b.page, c.page, d.page]) {
        await expect(page.getByRole('heading', { name: LOBBY_HEADING })).toBeVisible({
          timeout: 10_000,
        })
      }

      // D's join response already includes the waiting player in its roster. Existing clients keep
      // their live room sockets through RETURN_TO_LOBBY and resync when the rematch starts.
      await expect(d.page.getByText(PLAYERS.d, { exact: true })).toBeVisible()

      // Existing players keep their ready state across the rematch; only the promoted late joiner
      // needs to opt in.
      await ready(d.page)
      await expect(start).toBeEnabled({ timeout: 10_000 })
      await start.click()
      await Promise.all(
        [a.page, b.page, c.page, d.page].map((page) =>
          expect(page).toHaveURL(new RegExp(`/r/${code}/play$`), { timeout: 10_000 }),
        ),
      )
      await expect(a.page.getByRole('listitem', { name: PLAYERS.d })).toBeVisible()
    } finally {
      await Promise.all(contexts.map((context) => context.close()))
    }
  })
})
