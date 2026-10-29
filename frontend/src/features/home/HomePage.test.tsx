import { render, screen, within } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import userEvent from '@testing-library/user-event'
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

  it('shows the five listening levels with the base score of each (BR-01, BR-02)', () => {
    renderPage()

    const levels = within(screen.getByRole('list', { name: 'Năm mốc nghe trong một vòng' }))
    const items = levels.getAllByRole('listitem')
    expect(items).toHaveLength(5)
    const expected = [
      [2, '1.000'],
      [5, '800'],
      [10, '600'],
      [20, '400'],
      [30, '200'],
    ] as const
    expected.forEach(([seconds, points], index) => {
      expect(items[index]).toHaveTextContent(new RegExp(`${String(seconds)} giây.*${points} điểm`))
    })
  })

  it('explains how a round works in four steps', () => {
    renderPage()

    const how = screen.getByRole('region', { name: 'Một vòng chơi' })
    const steps = within(how).getAllByRole('listitem')
    expect(steps).toHaveLength(4)
    expect(steps[0]).toHaveTextContent('Nghe 2 giây đầu.')
    expect(steps[3]).toHaveTextContent('Càng sớm, càng nhiều điểm.')
  })

  it('keeps the decoration out of the accessibility tree and out of the tab order', () => {
    const { container } = renderPage()

    expect(container.querySelectorAll('svg[aria-hidden="true"]').length).toBeGreaterThan(0)
    expect(container.querySelectorAll('.mh-roll')).toHaveLength(2)
    for (const counter of container.querySelectorAll('.mh-roll')) {
      expect(counter.closest('[aria-hidden="true"]')).not.toBeNull()
    }
    // Only the real links are focusable: the hero action, the solo tile and the music picker button.
    expect(screen.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/solo',
      '/solo',
      '/solo',
    ])
  })

  it('shows a nine-month calendar that is labelled as a preview and hidden from assistive tech', () => {
    const { container } = renderPage()

    const section = screen.getByRole('region', { name: 'Thắp sáng mỗi ngày' })
    expect(within(section).getByText('Minh họa')).toBeInTheDocument()

    const cells = container.querySelectorAll('.mh-streak .mh-cell')
    // 40 weeks of 7 days, minus the days of this week that have not come yet.
    expect(cells.length).toBeGreaterThanOrEqual(40 * 7 - 6)
    expect(cells.length).toBeLessThanOrEqual(40 * 7)
    expect(container.querySelector('.mh-streak')).toHaveAttribute('aria-hidden', 'true')
  })

  it('draws today as a cell still waiting to be lit, never as a played day', () => {
    const { container } = renderPage()

    const today = container.querySelectorAll('.mh-cell[data-today="true"]')
    expect(today).toHaveLength(1)
    expect(today[0]).toHaveAttribute('data-level', '0')
  })

  it('lights some days in all five shades, by the level the challenge was guessed at', () => {
    const { container } = renderPage()

    for (const level of ['1', '2', '3', '4', '5']) {
      expect(container.querySelector(`.mh-streak .mh-cell[data-level="${level}"]`)).not.toBeNull()
    }
  })

  it('has pips under both counters, one set filling and one draining', () => {
    const { container } = renderPage()

    expect(container.querySelectorAll('.mh-pip-fill-5')).toHaveLength(1)
    expect(container.querySelectorAll('.mh-pip-drain-5')).toHaveLength(1)
    expect(container.querySelectorAll('.mh-roll')).toHaveLength(2)
  })

  it('starts on the first step and lets the player pick any step', async () => {
    const user = userEvent.setup()
    renderPage()

    const how = screen.getByRole('region', { name: 'Một vòng chơi' })
    const steps = within(how).getAllByRole('button')
    expect(steps).toHaveLength(4)
    expect(steps[0]).toHaveAttribute('aria-current', 'step')
    expect(steps[3]).not.toHaveAttribute('aria-current')

    await user.click(steps[3] as HTMLElement)
    expect(steps[3]).toHaveAttribute('aria-current', 'step')
    expect(steps[0]).not.toHaveAttribute('aria-current')
  })

  it('shows the made-up round change with the step, and hides it from assistive tech', async () => {
    const user = userEvent.setup()
    const { container } = renderPage()
    const how = screen.getByRole('region', { name: 'Một vòng chơi' })
    const steps = within(how).getAllByRole('button')
    const mock = () => how.querySelector('[aria-hidden="true"].rounded-3xl')

    expect(mock()).not.toBeNull()
    expect(mock()).toHaveTextContent('Tên bài hát…')
    expect(mock()).not.toHaveTextContent('+800')

    await user.click(steps[2] as HTMLElement)
    expect(mock()).toHaveTextContent('bai hat bi')
    expect(mock()).toHaveTextContent('Bài hát bí ẩn')

    await user.click(steps[3] as HTMLElement)
    expect(mock()).toHaveTextContent('+800')
    expect(mock()).toHaveTextContent('Đúng ở mốc 5s')
    expect(container.querySelectorAll('.mh-step-bar')).toHaveLength(1)
  })

  it('puts the name at the top as decoration, and introduces the range of music', () => {
    const { container } = renderPage()

    const wordmark = container.querySelector('p[aria-hidden="true"]')
    expect(wordmark).toHaveTextContent('Mel')
    expect(wordmark?.querySelectorAll('svg')).toHaveLength(2)

    const mix = screen.getByRole('region', { name: 'Nghe đủ mọi gu' })
    for (const name of ['V\u2011pop', 'K\u2011pop', 'US\u2011UK']) {
      expect(within(mix).getByText(name)).toBeInTheDocument()
    }
    for (const facet of ['Thể loại', 'Quốc gia', 'Thập niên', 'Ca sĩ', 'Bộ đề tuyển chọn']) {
      expect(within(mix).getByText(facet)).toBeInTheDocument()
    }
    expect(within(mix).getByRole('link', { name: /Chọn bộ nhạc của bạn/ })).toHaveAttribute(
      'href',
      '/solo',
    )
  })

  it('speaks English when the language is switched', async () => {
    await i18n.changeLanguage('en')
    renderPage()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Hear less. Guess faster.')
    expect(screen.getAllByText('Coming soon')).toHaveLength(3)
  })
})
