import { render, screen, within } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { HomePage } from './HomePage'

function renderPage() {
  return render(
    <I18nextProvider i18n={i18n}>
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    </I18nextProvider>,
  )
}

describe('HomePage', () => {
  it('has one h1 and a primary action to solo play', () => {
    renderPage()

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(screen.getAllByRole('link', { name: 'Chơi đơn' })[0]).toHaveAttribute('href', '/solo')
  })

  it('lists the four entries, with the unbuilt ones marked "Sắp có" and not clickable', () => {
    renderPage()

    const modes = screen.getByRole('region', { name: 'Chọn cách chơi' })
    expect(within(modes).getByRole('link', { name: /Chơi đơn/ })).toHaveAttribute('href', '/solo')
    for (const title of ['Thử thách ngày', 'Tạo phòng', 'Ghép trận nhanh']) {
      expect(within(modes).getByText(title)).toBeInTheDocument()
      expect(within(modes).queryByRole('link', { name: new RegExp(title) })).not.toBeInTheDocument()
    }
    expect(within(modes).getAllByText('Sắp có')).toHaveLength(3)
  })

  it('shows the five listening levels', () => {
    renderPage()

    const levels = within(screen.getByRole('list', { name: 'Năm mốc nghe trong một vòng' }))
    expect(levels.getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      '2 giây',
      '5 giây',
      '10 giây',
      '20 giây',
      '30 giây',
    ])
  })

  it('speaks English when the language is switched', async () => {
    await i18n.changeLanguage('en')
    renderPage()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Hear less. Guess faster.')
    expect(screen.getAllByText('Coming soon')).toHaveLength(3)
  })
})
