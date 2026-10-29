import type { components } from '@/shared/api/schema.gen'

export type SoloFilter = components['schemas']['SoloFilter']

/** The contract caps every list of a filter at 20 values. */
export const MAX_PER_GROUP = 20

export interface MusicFilter {
  genreCodes: string[]
  countryCodes: string[]
  decades: number[]
  artistIds: string[]
}

/**
 * What the player chose. A playlist and a filter are two different ways to name a set of tracks,
 * so only `source` counts; the other side is kept so switching tabs does not lose the choice.
 */
export interface MusicSelection {
  source: 'filter' | 'playlist'
  filter: MusicFilter
  playlistId: string | null
}

export const EMPTY_SELECTION: MusicSelection = {
  source: 'filter',
  filter: { genreCodes: [], countryCodes: [], decades: [], artistIds: [] },
  playlistId: null,
}

export function countFilterValues(filter: MusicFilter): number {
  return (
    filter.genreCodes.length +
    filter.countryCodes.length +
    filter.decades.length +
    filter.artistIds.length
  )
}

/** The contract reads a missing or empty list as "no filter", so empty lists are left out. */
export function toSoloFilter(filter: MusicFilter): SoloFilter {
  return {
    ...(filter.genreCodes.length > 0 && { genreCodes: filter.genreCodes }),
    ...(filter.countryCodes.length > 0 && { countryCodes: filter.countryCodes }),
    ...(filter.decades.length > 0 && { decades: filter.decades }),
    ...(filter.artistIds.length > 0 && { artistIds: filter.artistIds }),
  }
}
