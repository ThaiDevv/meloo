import { describe, expect, it } from 'vitest'
import { EMPTY_SELECTION } from '@/shared/music-picker'
import { room } from '@/shared/realtime/testing/fixtures'
import { isIncomplete, musicRequest, musicSettings, selectionOf } from './music'

const filterSelection = {
  ...EMPTY_SELECTION,
  filter: { genreCodes: ['POP'], countryCodes: [], decades: [2020], artistIds: [] },
}
const playlistSelection = { ...EMPTY_SELECTION, source: 'playlist' as const, playlistId: 'p-1' }

describe('selectionOf', () => {
  it('reads a playlist room as the playlist tab', () => {
    expect(selectionOf(room({ playlistId: 'p-1' }))).toMatchObject({
      source: 'playlist',
      playlistId: 'p-1',
    })
  })

  it('reads a filter room as the filter tab with every group filled in', () => {
    expect(selectionOf(room({ playlistId: null, filter: { genreCodes: ['POP'] } }))).toMatchObject({
      source: 'filter',
      filter: { genreCodes: ['POP'], countryCodes: [], decades: [], artistIds: [] },
    })
  })

  it('reads a room without either as the whole catalog', () => {
    expect(selectionOf(room({ playlistId: null, filter: null }))).toEqual(EMPTY_SELECTION)
  })
})

describe('musicSettings (the /settings command)', () => {
  it('sends only the playlist for a playlist', () => {
    expect(musicSettings(playlistSelection)).toEqual({ playlistId: 'p-1' })
  })

  it('sends only the non-empty groups of a filter', () => {
    expect(musicSettings(filterSelection)).toEqual({
      filter: { genreCodes: ['POP'], decades: [2020] },
    })
  })

  it('sends an empty filter to go back to the whole catalog', () => {
    expect(musicSettings(EMPTY_SELECTION)).toEqual({ filter: {} })
  })
})

describe('musicRequest (the POST /rooms body)', () => {
  it('leaves both out for the whole catalog, and never sends both', () => {
    expect(musicRequest(EMPTY_SELECTION)).toEqual({})
    expect(musicRequest(playlistSelection)).toEqual({ playlistId: 'p-1' })
    expect(musicRequest(filterSelection)).toEqual({
      filter: { genreCodes: ['POP'], decades: [2020] },
    })
  })
})

describe('isIncomplete', () => {
  it('is true for the playlist tab without a playlist', () => {
    expect(isIncomplete({ ...EMPTY_SELECTION, source: 'playlist' })).toBe(true)
    expect(isIncomplete(playlistSelection)).toBe(false)
    expect(isIncomplete(EMPTY_SELECTION)).toBe(false)
  })
})
