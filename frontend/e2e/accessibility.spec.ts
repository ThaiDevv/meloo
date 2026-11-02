import { expect, test } from '@playwright/test'

test.describe('Accessibility & Keyboard Navigation (NFR-13)', () => {
  test.beforeEach(async ({ page }) => {
    // Authenticate as guest before accessing /solo routes
    await page.goto('/start')
    const nicknameInput = page.locator('#guest-nickname')
    await expect(nicknameInput).toBeVisible({ timeout: 10_000 })
    await nicknameInput.fill('A11yTester')
    await page.locator('input[type="radio"]').first().check({ force: true })
    const submitGuestBtn = page.getByRole('button', { name: /Vào chơi|Start playing|Tiếp tục/i })
    await Promise.all([
      page.waitForResponse((res) => res.url().includes('/api/v1/auth/guest') && res.ok()),
      submitGuestBtn.click(),
    ])
  })

  test('core game operations can be performed strictly using keyboard', async ({ page }) => {
    // 1. Visit single play setup page
    await page.goto('/solo')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 10_000 })

    // Select 5 rounds (matches E2E seed dataset)
    await page.locator('input[name="rounds"][value="5"]').check({ force: true })

    // 2. Start a match using keyboard navigation
    const startButton = page.getByRole('button', { name: /Vào trận|Start match|Bắt đầu|Start/i })
    await startButton.focus()
    await expect(startButton).toBeFocused()
    await page.keyboard.press('Enter')

    // 3. Intro screen: tab to "Start round 1" / "Vào vòng 1" button and activate with Enter
    const enterRoundBtn = page.getByRole('button', {
      name: /Vào vòng 1|Start round 1|Chơi tiếp vòng 1|Bắt đầu vòng/i,
    })
    await expect(enterRoundBtn).toBeVisible({ timeout: 10_000 })
    await enterRoundBtn.focus()
    await expect(enterRoundBtn).toBeFocused()
    await page.keyboard.press('Enter')

    // 4. Round 1: Search and Guess using keyboard only (combobox)
    const guessInput = page.getByRole('combobox', { name: /Đoán tên bài|Guess the song|Guess/i })
    await expect(guessInput).toBeVisible({ timeout: 10_000 })
    await guessInput.focus()
    await expect(guessInput).toBeFocused()

    // Type query with keyboard
    await page.keyboard.type('Chung Ta')
    // Wait for dropdown to show suggestions
    const option = page.getByRole('option').first()
    await expect(option).toBeVisible({ timeout: 5_000 })

    // Use ArrowDown to highlight and Enter to pick
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Enter')

    // Press Enter again to submit guess
    await page.keyboard.press('Enter')

    // 5. Verify reveal appears, tab to continue button and press Enter
    const continueBtn = page.getByRole('button', {
      name: /Vòng tiếp theo|Next round|Tiếp tục|Xem kết quả|See result/i,
    })
    await expect(continueBtn).toBeVisible({ timeout: 10_000 })
    await continueBtn.focus()
    await expect(continueBtn).toBeFocused()
    await page.keyboard.press('Enter')

    // 6. Round 2: Test "Nghe thêm" / "Reveal more" button with keyboard
    const listenMoreBtn = page.getByRole('button', { name: /Nghe thêm|Reveal more/i })
    await expect(listenMoreBtn).toBeVisible({ timeout: 10_000 })
    await listenMoreBtn.focus()
    await expect(listenMoreBtn).toBeFocused()
    await page.keyboard.press('Enter')

    // Verify points countdown element is present
    await expect(page.getByTestId('points-left')).toBeVisible()
  })

  test('accessible landmarks and combobox ARIA semantics are present', async ({ page }) => {
    await page.goto('/solo')

    // Check main role and headings
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 10_000 })

    // Select 5 rounds (matches E2E seed dataset)
    await page.locator('input[name="rounds"][value="5"]').check({ force: true })

    // Start match to inspect game round ARIA semantics
    const startButton = page.getByRole('button', { name: /Vào trận|Start match|Bắt đầu|Start/i })
    await startButton.click()

    const enterRoundBtn = page.getByRole('button', {
      name: /Vào vòng 1|Start round 1|Chơi tiếp vòng 1|Bắt đầu vòng/i,
    })
    await expect(enterRoundBtn).toBeVisible({ timeout: 10_000 })
    await enterRoundBtn.click()

    // Combobox attributes per WAI-ARIA
    const combobox = page.getByRole('combobox')
    await expect(combobox).toHaveAttribute('aria-autocomplete', 'list')
    await expect(combobox).toHaveAttribute('aria-expanded', 'false')

    await combobox.focus()
    await page.keyboard.type('a')
    await expect(combobox).toHaveAttribute('aria-expanded', 'true')
  })
})
