import { expect, test } from '@playwright/test'

test('home page loads and the language can be switched', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByTestId('placeholder')).toBeVisible()
  await page.getByRole('button', { name: 'EN' }).click()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Home')
})
