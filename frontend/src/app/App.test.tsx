import { render } from '@testing-library/react'
import { StrictMode } from 'react'
import { describe, expect, it, vi } from 'vitest'

describe('App', () => {
  it('registers a single history listener under StrictMode (no leaked router)', async () => {
    vi.resetModules()
    const add = vi.spyOn(window, 'addEventListener')
    const { App } = await import('./App')
    render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
    expect(add.mock.calls.filter(([type]) => type === 'popstate')).toHaveLength(1)
    add.mockRestore()
  })
})
