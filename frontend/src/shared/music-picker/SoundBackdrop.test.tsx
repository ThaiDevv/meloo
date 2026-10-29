import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SoundBackdrop } from './SoundBackdrop'

const started = vi.fn()
const constructed = vi.fn()

/** Just enough of the Web Audio API for one struck note. */
class FakeAudioContext {
  currentTime = 0
  state = 'running'
  destination = {}
  constructor() {
    constructed()
  }
  resume() {
    return Promise.resolve()
  }
  createGain() {
    return {
      gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
      connect: vi.fn(),
    }
  }
  createOscillator() {
    return { type: '', frequency: { value: 0 }, connect: vi.fn(), start: started, stop: vi.fn() }
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
  started.mockClear()
  constructed.mockClear()
})

function pressPage() {
  act(() => {
    fireEvent.pointerDown(window, { clientX: 100, clientY: 80 })
  })
}

describe('SoundBackdrop', () => {
  it('keeps the sound off until the player turns it on', async () => {
    const user = userEvent.setup()
    render(<SoundBackdrop />)

    const button = screen.getByRole('button', { name: 'Âm thanh' })
    expect(button).toHaveAttribute('aria-pressed', 'false')
    await user.click(button)
    expect(button).toHaveAttribute('aria-pressed', 'true')
    await user.click(button)
    expect(button).toHaveAttribute('aria-pressed', 'false')
  })

  it('leaves a ring where the page is pressed, and removes it when it has faded', () => {
    vi.useFakeTimers()
    render(<SoundBackdrop />)

    pressPage()
    expect(screen.getByTestId('ping')).toHaveStyle({ left: '100px', top: '80px' })

    act(() => {
      vi.advanceTimersByTime(1200)
    })
    expect(screen.queryByTestId('ping')).not.toBeInTheDocument()
    vi.useRealTimers()
  })

  it('keeps at most eight rings at once', () => {
    render(<SoundBackdrop />)

    for (let i = 0; i < 12; i++) pressPage()
    expect(screen.getAllByTestId('ping')).toHaveLength(8)
  })

  it('plays no note while the sound is off, and one note per press once it is on', async () => {
    vi.stubGlobal('AudioContext', FakeAudioContext)
    const user = userEvent.setup()
    render(<SoundBackdrop />)

    pressPage()
    expect(constructed).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Âm thanh' }))
    pressPage()
    pressPage()
    expect(constructed).toHaveBeenCalledTimes(1)
    expect(started).toHaveBeenCalledTimes(2)
  })

  it('draws no rings under reduced motion', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    render(<SoundBackdrop />)

    pressPage()
    expect(screen.queryByTestId('ping')).not.toBeInTheDocument()
  })

  it('hides the decoration from assistive technology', () => {
    const { container } = render(<SoundBackdrop />)

    expect(container.querySelectorAll('[aria-hidden="true"]').length).toBeGreaterThanOrEqual(2)
  })
})
