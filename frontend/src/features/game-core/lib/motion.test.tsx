import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useCountTo } from './motion'

function Counter({ value }: { value: number }) {
  return <p>{useCountTo(value, { ms: 600 })}</p>
}

function stubMotion(reduce: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: reduce && query.includes('reduce') }))
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame', 'performance'] })
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('useCountTo', () => {
  it('runs from the old number to the new one', () => {
    stubMotion(false)
    const view = render(<Counter value={800} />)
    view.rerender(<Counter value={600} />)
    expect(screen.getByText('800')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(200)
    })
    const midway = Number(screen.getByRole('paragraph').textContent)
    expect(midway).toBeLessThan(800)
    expect(midway).toBeGreaterThan(600)

    act(() => {
      vi.advanceTimersByTime(600)
    })
    expect(screen.getByText('600')).toBeInTheDocument()
  })

  it('jumps straight to the number under prefers-reduced-motion', () => {
    stubMotion(true)
    const view = render(<Counter value={800} />)
    view.rerender(<Counter value={600} />)
    expect(screen.getByText('600')).toBeInTheDocument()
  })
})
