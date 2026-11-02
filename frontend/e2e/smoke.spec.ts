import { expect, test } from '@playwright/test'

test('home page loads and the language can be switched', async ({ page }) => {
  await page.goto('/')
  const heading = page.getByRole('heading', { level: 1 })
  await expect(heading).toBeVisible()

  // Switch to English if not already
  const enButton = page.getByRole('button', { name: 'EN', exact: true })
  await enButton.click()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')

  // Switch to Vietnamese
  const viButton = page.getByRole('button', { name: 'VI', exact: true })
  await viButton.click()
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi')
})
