import { describe, expect, it } from 'vitest'
import { pageIndexFromParam, secondsForLevel } from './format'

describe('secondsForLevel', () => {
  it('maps the five levels to their clip length', () => {
    expect([1, 2, 3, 4, 5].map(secondsForLevel)).toEqual([2, 5, 10, 20, 30])
  })

  it('returns null outside 1..5', () => {
    expect(secondsForLevel(0)).toBeNull()
    expect(secondsForLevel(6)).toBeNull()
    expect(secondsForLevel(1.5)).toBeNull()
  })
})

describe('pageIndexFromParam', () => {
  it('turns the 1-based URL page into a 0-based index', () => {
    expect(pageIndexFromParam('1')).toBe(0)
    expect(pageIndexFromParam('3')).toBe(2)
  })

  it('falls back to the first page on missing or bad values', () => {
    expect(pageIndexFromParam(null)).toBe(0)
    expect(pageIndexFromParam('0')).toBe(0)
    expect(pageIndexFromParam('-2')).toBe(0)
    expect(pageIndexFromParam('abc')).toBe(0)
  })
})
