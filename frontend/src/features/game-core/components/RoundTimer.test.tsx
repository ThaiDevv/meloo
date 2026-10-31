import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RoundTimer } from './RoundTimer'

beforeEach(() => {
  vi.useFakeTimers()
})
afterEach(() => {
  vi.useRealTimers()
})

// The deadline is 45 s after the server's clock, whatever the device clock says (BR-07).
const timer = { serverTime: '2026-10-23T13:10:00Z', endsAt: '2026-10-23T13:10:45Z' }

describe('RoundTimer', () => {
  it('counts down from the time the server left', () => {
    render(<RoundTimer timer={timer} />)
    expect(screen.getByRole('timer', { name: 'Thời gian còn lại' })).toHaveAttribute(
      'aria-valuetext',
      '45 giây',
    )
    act(() => {
      vi.advanceTimersByTime(15_000)
    })
    expect(screen.getByRole('timer')).toHaveAttribute('aria-valuetext', '30 giây')
    expect(screen.getByText('0:30')).toBeInTheDocument()
  })

  it('fires onTimeout once when the time runs out (AC: hết giờ)', () => {
    const onTimeout = vi.fn()
    render(<RoundTimer timer={timer} onTimeout={onTimeout} />)
    act(() => {
      vi.advanceTimersByTime(44_900)
    })
    expect(onTimeout).not.toHaveBeenCalled()
    act(() => {
      vi.advanceTimersByTime(5_000)
    })
    expect(onTimeout).toHaveBeenCalledOnce()
    expect(screen.getByText('0:00')).toBeInTheDocument()
  })
})
