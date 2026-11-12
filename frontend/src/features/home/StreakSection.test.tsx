import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useSessionStore } from '@/shared/session/store'
import historyExample from '../../../../contracts/examples/daily/daily-history.200.json'
import { HomePage } from './HomePage'
import { StreakSection } from './StreakSection'

function renderSection(ui = <StreakSection />) {
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter>{ui}</MemoryRouter>
      </QueryClientProvider>
    </I18nextProvider>,
  )
}

const cell = (container: HTMLElement, day: string) =>
  container.querySelector<HTMLElement>(`.mh-cell[data-day="${day}"]`)

afterEach(() => {
  useSessionStore.getState().setRole(null)
})

describe('StreakSection without a session', () => {
  it('keeps the labelled picture and asks the server nothing', () => {
    let asked = false
    server.use(
      http.get('*/api/v1/daily/history', () => {
        asked = true
        return HttpResponse.json(historyExample)
      }),
    )
    const { container } = renderSection()

    expect(screen.getByText('Minh họa')).toBeInTheDocument()
    expect(container.querySelector('.mh-streak')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.queryByTestId('streak-summary')).not.toBeInTheDocument()
    expect(asked).toBe(false)
  })
})

describe('StreakSection with a session', () => {
  it('shows a skeleton, then the real days, and drops the "Minh họa" label', async () => {
    useSessionStore.getState().setRole('USER')
    renderSection()

    expect(screen.getByRole('status')).toHaveTextContent('Đang tải lịch…')
    expect(await screen.findByTestId('streak-summary')).toHaveTextContent(
      'Bạn đã đoán ra 11 ngày, chuỗi hiện tại 3 ngày, dài nhất 5 ngày.',
    )
    expect(screen.queryByText('Minh họa')).not.toBeInTheDocument()
  })

  it('lights a day by the level it was guessed at: 30 seconds faintest, 2 seconds brightest', async () => {
    useSessionStore.getState().setRole('USER')
    const { container } = renderSection()
    await screen.findByTestId('streak-summary')

    const levelOf = (day: string) => cell(container, day)?.getAttribute('data-level')
    expect(levelOf('2026-10-12')).toBe('1') // 30s
    expect(levelOf('2026-10-13')).toBe('2') // 20s
    expect(levelOf('2026-10-14')).toBe('3') // 10s
    expect(levelOf('2026-10-15')).toBe('4') // 5s
    expect(levelOf('2026-10-16')).toBe('5') // 2s
  })

  it('leaves a day that is not in the list dark, including a day played but not guessed', async () => {
    useSessionStore.getState().setRole('USER')
    const { container } = renderSection()
    await screen.findByTestId('streak-summary')

    // 17–19 October are absent: the server only lists days that were guessed.
    for (const day of ['2026-10-17', '2026-10-18', '2026-10-19']) {
      expect(cell(container, day)).toHaveAttribute('data-level', '0')
    }
  })

  it('marks today from the server, not from the device clock', async () => {
    useSessionStore.getState().setRole('USER')
    const { container } = renderSection()
    await screen.findByTestId('streak-summary')

    const today = container.querySelectorAll('.mh-cell[data-today="true"]')
    expect(today).toHaveLength(1)
    expect(today[0]).toHaveAttribute('data-day', historyExample.today)
    // Days after the server's today are not drawn, whatever the device thinks the date is.
    expect(cell(container, '2026-10-30')).toBeNull()
    expect(cell(container, '2026-11-12')).toBeNull()
  })

  it('gives every lit day a tooltip and takes no focus', async () => {
    useSessionStore.getState().setRole('USER')
    const { container } = renderSection()
    await screen.findByTestId('streak-summary')

    expect(cell(container, '2026-10-14')).toHaveAttribute('title', '14/10: Đúng ở 10s')
    expect(cell(container, '2026-10-17')).not.toHaveAttribute('title')
    expect(container.querySelector('.mh-streak')).toHaveAttribute('aria-hidden', 'true')
    expect(container.querySelectorAll('.mh-streak [tabindex]')).toHaveLength(0)
  })

  it('speaks English in the summary and the tooltip', async () => {
    await i18n.changeLanguage('en')
    useSessionStore.getState().setRole('USER')
    const { container } = renderSection()

    expect(await screen.findByTestId('streak-summary')).toHaveTextContent(
      'You guessed it on 11 days. Current streak: 3. Longest: 5.',
    )
    expect(cell(container, '2026-10-14')).toHaveAttribute('title', '10/14: Right at 10s')
  })

  it('shows an empty calendar for a new player, without the "Minh họa" label', async () => {
    server.use(
      http.get('*/api/v1/daily/history', () =>
        HttpResponse.json({ today: '2026-10-29', items: [], currentStreak: 0, longestStreak: 0 }),
      ),
    )
    useSessionStore.getState().setRole('GUEST')
    const { container } = renderSection()

    expect(await screen.findByTestId('streak-summary')).toHaveTextContent(
      'Bạn đã đoán ra 0 ngày, chuỗi hiện tại 0 ngày, dài nhất 0 ngày.',
    )
    expect(screen.queryByText('Minh họa')).not.toBeInTheDocument()
    expect(
      container.querySelectorAll('.mh-streak .mh-cell[data-level]:not([data-level="0"])'),
    ).toHaveLength(0)
  })

  it('shows an error with a retry, and retrying loads the days', async () => {
    let fail = true
    server.use(
      http.get('*/api/v1/daily/history', () =>
        fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(historyExample),
      ),
    )
    useSessionStore.getState().setRole('USER')
    renderSection()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Chưa tải được lịch')
    fail = false
    await userEvent.setup().click(within(alert).getByRole('button'))
    expect(await screen.findByTestId('streak-summary')).toBeInTheDocument()
  })

  it('does not stop the rest of the home page when the calendar fails', async () => {
    server.use(http.get('*/api/v1/daily/history', () => new HttpResponse(null, { status: 500 })))
    useSessionStore.getState().setRole('USER')
    renderSection(<HomePage />)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Một vòng chơi' })).toBeInTheDocument()
  })
})
