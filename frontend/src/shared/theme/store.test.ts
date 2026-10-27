import { beforeEach, describe, expect, it } from 'vitest'
import { useThemeStore } from './store'

describe('theme store', () => {
  beforeEach(() => {
    localStorage.clear()
    useThemeStore.getState().setTheme('dark')
  })

  it('is dark by default and marks <html> as dark', () => {
    expect(useThemeStore.getState().theme).toBe('dark')
    expect(document.documentElement).toHaveClass('dark')
  })

  it('switches to light, updates <html> and remembers the choice', () => {
    useThemeStore.getState().toggle()
    expect(useThemeStore.getState().theme).toBe('light')
    expect(document.documentElement).not.toHaveClass('dark')
    expect(document.documentElement.style.colorScheme).toBe('light')
    expect(localStorage.getItem('meloo.theme')).toBe('light')
  })
})
