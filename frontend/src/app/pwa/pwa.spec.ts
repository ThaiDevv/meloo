import { expect, test } from '@playwright/test'

test('installs from build and keeps offline navigation separate from API and audio', async ({
  page,
  context,
  request,
}, testInfo) => {
  await page.goto('/')
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready
    if (!navigator.serviceWorker.controller) {
      await new Promise<void>((resolve) => {
        navigator.serviceWorker.addEventListener(
          'controllerchange',
          () => {
            resolve()
          },
          {
            once: true,
          },
        )
      })
    }
  })
  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute(
    'href',
    '/manifest.webmanifest',
  )
  await expect(page.locator('script[src="/registerSW.js"]')).toHaveAttribute('defer', '')
  const manifest = await request.get('/manifest.webmanifest')
  expect(manifest.ok()).toBe(true)
  expect(await manifest.json()).toMatchObject({
    id: '/',
    name: 'Meloo',
    short_name: 'Meloo',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    background_color: '#0d0d0e',
    theme_color: '#0d0d0e',
    icons: expect.arrayContaining([
      expect.objectContaining({ sizes: '192x192', purpose: 'any' }),
      expect.objectContaining({ sizes: '512x512', purpose: 'any' }),
      expect.objectContaining({ sizes: '512x512', purpose: 'maskable' }),
    ]),
  })
  for (const [file, size] of [
    ['icon-192.png', 192],
    ['icon-512.png', 512],
    ['maskable-512.png', 512],
    ['apple-touch-icon.png', 180],
  ] as const) {
    const icon = await request.get(`/pwa/${file}`)
    expect(icon.ok()).toBe(true)
    const png = await icon.body()
    expect(png.readUInt32BE(16)).toBe(size)
    expect(png.readUInt32BE(20)).toBe(size)
  }
  const cdp = await context.newCDPSession(page)
  await cdp.send('Page.enable')
  await expect(cdp.send('Page.getInstallabilityErrors')).resolves.toMatchObject({
    installabilityErrors: [],
  })
  // Use synthetic responses: no authentication or real audio tokens are needed.
  const sensitive = [
    '/api/v1/me',
    '/api/v1/audio/pwa-test',
    '/audio/pwa-test',
    '/preview.mp3',
    '/ws',
  ]
  for (const path of sensitive) {
    await context.route(`**${path}`, (route) =>
      route.fulfill({
        status: 200,
        body: 'pwa-test-network-response',
        headers: { 'Cache-Control': 'no-store' },
      }),
    )
    expect(await page.evaluate(async (path) => (await fetch(path)).text(), path)).toBe(
      'pwa-test-network-response',
    )
    await context.unroute(`**${path}`)
  }
  const cachedPaths = await page.evaluate(async () => {
    const paths: string[] = []
    for (const name of await caches.keys()) {
      const cache = await caches.open(name)
      for (const entry of await cache.keys()) paths.push(new URL(entry.url).pathname)
    }
    return paths.sort()
  })
  expect(cachedPaths).toContain('/offline.html')
  expect(
    cachedPaths.every(
      (path) =>
        path === '/offline.html' || path === '/manifest.webmanifest' || path.startsWith('/pwa/'),
    ),
  ).toBe(true)
  await testInfo.attach('cached-paths', {
    body: JSON.stringify(cachedPaths),
    contentType: 'application/json',
  })
  await context.setOffline(true)
  for (const path of sensitive) {
    expect(
      await page.evaluate(async (path) => {
        try {
          await fetch(path)
          return 'unexpected-response'
        } catch {
          return 'network-failure'
        }
      }, path),
    ).toBe('network-failure')
  }
  await page.goto('/solo?offline-test=1')
  const english = testInfo.project.name === 'desktop-en'
  await expect(
    page.getByRole('heading', { name: english ? 'The music is on pause' : 'Nhạc đang tạm dừng' }),
  ).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', english ? 'en' : 'vi')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button').focus()
  await expect(page.getByRole('button')).toBeFocused()
  await testInfo.attach('offline', {
    body: await page.screenshot({ path: testInfo.outputPath('offline.png') }),
    contentType: 'image/png',
  })
  const otherPage = await context.newPage()
  for (const path of sensitive) await expect(otherPage.goto(path)).rejects.toThrow()
  await otherPage.close()
  await context.setOffline(false)
  await page.getByRole('button').click()
  await expect(page.locator('#root')).toBeAttached()
  await expect(page.getByRole('heading', { name: 'Nhạc đang tạm dừng' })).toHaveCount(0)
})

test('offline copy respects saved language and survives blocked storage', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('i18nextLng', 'en-US')
  })
  await page.goto('/offline.html')
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await expect(page.getByRole('button', { name: 'Try reconnecting' })).toBeVisible()
  await page.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', {
      get() {
        throw new Error('Storage blocked')
      },
    })
  })
  await page.reload()
  await expect(page.getByRole('heading')).toBeVisible()
})
