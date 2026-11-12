import { describe, expect, it } from 'vitest'
import {
  addDays,
  brightnessOf,
  formatDayMonth,
  formatDayMonthYear,
  localDay,
  weekdayIndex,
} from './dates'

describe('daily dates', () => {
  it('moves a yyyy-MM-dd day across month and year ends without a time zone', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
    expect(addDays('2026-10-29', -279)).toBe('2026-01-23')
  })

  it('counts weekdays from Monday', () => {
    expect(weekdayIndex('2026-10-26')).toBe(0) // Monday
    expect(weekdayIndex('2026-11-01')).toBe(6) // Sunday
  })

  it('formats a day for the interface language, and the share date the same everywhere', () => {
    expect(formatDayMonth('2026-10-22', 'vi')).toBe('22/10')
    expect(formatDayMonth('2026-10-22', 'en')).toBe('10/22')
    expect(formatDayMonthYear('2026-10-22')).toBe('22/10/2026')
  })

  it('maps the guessed level to brightness: 2 seconds brightest, 30 seconds faintest', () => {
    expect([2, 5, 10, 20, 30].map(brightnessOf)).toEqual([5, 4, 3, 2, 1])
  })

  it('names the device day from its own calendar fields', () => {
    expect(localDay(new Date(2026, 10, 5, 23, 59))).toBe('2026-11-05')
  })
})
