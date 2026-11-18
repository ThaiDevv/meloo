import type { TrackFilters } from './api/catalogApi'

export const DECADES = [1970, 1980, 1990, 2000, 2010, 2020] as const
export type StatusFilter = 'all' | 'enabled' | 'disabled'

export interface Draft {
  q: string
  status: StatusFilter
  genreCode: string
  countryCode: string
  decade: string
}

export const EMPTY: Draft = { q: '', status: 'all', genreCode: '', countryCode: '', decade: '' }

/** Turns the form into API filters; blank fields are left out. */
export function toFilters(draft: Draft, page: number): TrackFilters {
  const country = draft.countryCode.trim().toUpperCase()
  return {
    q: draft.q.trim() || undefined,
    enabled: draft.status === 'all' ? undefined : draft.status === 'enabled',
    genreCode: draft.genreCode.trim() || undefined,
    countryCode: /^[A-Z]{2}$/.test(country) ? country : undefined,
    decade: draft.decade ? Number(draft.decade) : undefined,
    page,
  }
}
