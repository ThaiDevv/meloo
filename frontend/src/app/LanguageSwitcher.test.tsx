import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { LanguageSwitcher } from './LanguageSwitcher'
import { Providers } from './providers'
import i18n from '@/i18n'

describe('LanguageSwitcher', () => {
  it('switches the active language between vi and en', async () => {
    await i18n.changeLanguage('vi')
    render(
      <Providers>
        <LanguageSwitcher />
      </Providers>,
    )
    expect(screen.getByRole('button', { name: 'VI' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'EN' }))

    expect(i18n.resolvedLanguage).toBe('en')
    expect(screen.getByRole('button', { name: 'EN' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('group')).toHaveAccessibleName('Language')
  })
})
