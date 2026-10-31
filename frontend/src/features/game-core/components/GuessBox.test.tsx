import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockSuggestions } from '../mocks/fixtures'
import { GuessBox } from './GuessBox'

function setup(props: Partial<React.ComponentProps<typeof GuessBox>> = {}) {
  const onSearch = vi.fn()
  const onGuess = vi.fn()
  const user = userEvent.setup({ delay: null })
  render(
    <GuessBox suggestions={mockSuggestions} onSearch={onSearch} onGuess={onGuess} {...props} />,
  )
  return { onSearch, onGuess, user, box: screen.getByRole('combobox', { name: 'Đoán tên bài' }) }
}

/** The wait after the last keystroke, after which the host is asked and its suggestions count. */
function settle() {
  act(() => {
    vi.advanceTimersByTime(200)
  })
}

beforeEach(() => {
  vi.useFakeTimers()
  // Testing Library only drains its own timers under fake clocks when it sees Jest's global.
  Object.assign(globalThis, { jest: { advanceTimersByTime: vi.advanceTimersByTime.bind(vi) } })
})
afterEach(() => {
  vi.useRealTimers()
  Reflect.deleteProperty(globalThis, 'jest')
})

describe('GuessBox', () => {
  it('asks for suggestions 200 ms after the last keystroke, not on every key (AC 4)', async () => {
    const { onSearch, user, box } = setup()
    await user.type(box, 'chung')
    expect(onSearch).not.toHaveBeenCalled()

    act(() => {
      vi.advanceTimersByTime(199)
    })
    expect(onSearch).not.toHaveBeenCalled()
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(onSearch).toHaveBeenCalledOnce()
    expect(onSearch).toHaveBeenCalledWith('chung')
  })

  it('restarts the wait when typing continues', async () => {
    const { onSearch, user, box } = setup()
    await user.type(box, 'ch')
    act(() => {
      vi.advanceTimersByTime(150)
    })
    await user.type(box, 'un')
    act(() => {
      vi.advanceTimersByTime(150)
    })
    expect(onSearch).not.toHaveBeenCalled()
    act(() => {
      vi.advanceTimersByTime(50)
    })
    expect(onSearch).toHaveBeenCalledExactlyOnceWith('chun')
  })

  it('emphasises the match without needing accents', async () => {
    const { user, box } = setup()
    await user.type(box, 'chung ta')
    act(() => {
      vi.advanceTimersByTime(200)
    })
    const marks = screen.getAllByText('Chúng Ta', { selector: 'mark' })
    expect(marks.length).toBeGreaterThan(0)
  })

  it('picks with the arrow keys and Enter, then guesses with Enter (keyboard only)', async () => {
    const { onGuess, user, box } = setup()
    await user.type(box, 'chung')
    settle()
    expect(screen.getAllByRole('option')).toHaveLength(3)

    await user.keyboard('{ArrowDown}{ArrowDown}')
    expect(box).toHaveAttribute('aria-activedescendant', screen.getAllByRole('option')[1]?.id)
    await user.keyboard('{Enter}')
    expect(onGuess).not.toHaveBeenCalled()
    expect(box).toHaveValue('Chúng Ta Của Tương Lai – Sơn Tùng M-TP')

    await user.keyboard('{Enter}')
    expect(onGuess).toHaveBeenCalledExactlyOnceWith(mockSuggestions[1]?.id)
    expect(box).toHaveValue('')
  })

  it('keeps the guess button off until a suggestion is chosen', async () => {
    const { onGuess, user, box } = setup()
    const button = screen.getByRole('button', { name: 'Đoán' })
    await user.type(box, 'chung')
    settle()
    expect(button).toBeDisabled()
    await user.keyboard('{Enter}')
    expect(onGuess).not.toHaveBeenCalled()

    await user.click(screen.getAllByRole('option')[0] as HTMLElement)
    expect(button).toBeEnabled()
    await user.click(button)
    expect(onGuess).toHaveBeenCalledExactlyOnceWith(mockSuggestions[0]?.id)
  })

  it('does not offer suggestions of an earlier query for text typed since', async () => {
    const { onGuess, user, box } = setup()
    await user.type(box, 'chung')
    settle()
    expect(screen.getAllByRole('option')).toHaveLength(3)

    await user.type(box, ' xyz')
    expect(screen.queryAllByRole('option')).toHaveLength(0)
    // ArrowDown, Enter, Enter: the keystrokes that used to pick a stale song and guess it.
    await user.keyboard('{ArrowDown}{Enter}{Enter}')
    expect(onGuess).not.toHaveBeenCalled()
    expect(box).toHaveValue('chung xyz')
  })

  it('offers nothing while a search is still running', async () => {
    const { user, box } = setup({ loading: true })
    await user.type(box, 'chung')
    settle()
    expect(screen.queryAllByRole('option')).toHaveLength(0)
    expect(screen.getAllByText('Đang tìm…').length).toBeGreaterThan(0)
  })

  it('closes the list on Escape', async () => {
    const { user, box } = setup()
    await user.type(box, 'chung')
    settle()
    expect(box).toHaveAttribute('aria-expanded', 'true')
    await user.keyboard('{Escape}')
    expect(box).toHaveAttribute('aria-expanded', 'false')
  })

  it('says so when nothing matches, and while searching', async () => {
    const { user, box } = setup({ suggestions: [] })
    await user.type(box, 'zzz')
    expect(screen.getAllByText('Đang tìm…').length).toBeGreaterThan(0)
    settle()
    expect(screen.getAllByText('Không thấy bài nào khớp').length).toBeGreaterThan(0)
  })

  it('cannot guess while a request is in flight', () => {
    setup({ disabled: true })
    expect(screen.getByRole('combobox')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đoán' })).toBeDisabled()
  })
})
