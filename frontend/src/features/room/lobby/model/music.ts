import {
  EMPTY_SELECTION,
  countFilterValues,
  toSoloFilter,
  type MusicSelection,
} from '@/shared/music-picker'
import type { RoomResponse, SettingsCommand } from '@/shared/realtime/messages'

/** What the room plays, as the music picker understands it. */
export function selectionOf(room: RoomResponse): MusicSelection {
  if (room.playlistId) {
    return { ...EMPTY_SELECTION, source: 'playlist', playlistId: room.playlistId }
  }
  const filter = room.filter ?? {}
  return {
    ...EMPTY_SELECTION,
    source: 'filter',
    filter: {
      genreCodes: filter.genreCodes ?? [],
      countryCodes: filter.countryCodes ?? [],
      decades: filter.decades ?? [],
      artistIds: filter.artistIds ?? [],
    },
  }
}

/** True when the playlist tab is active but no playlist is picked yet: nothing to apply. */
export function isIncomplete(selection: MusicSelection): boolean {
  return selection.source === 'playlist' && !selection.playlistId
}

/**
 * The /settings command for a music choice. A playlist and a filter exclude each other and a
 * missing field means "unchanged", so only the chosen side is sent; an empty filter says "whole catalog".
 */
export function musicSettings(selection: MusicSelection): SettingsCommand {
  if (selection.source === 'playlist') return { playlistId: selection.playlistId }
  return { filter: countFilterValues(selection.filter) > 0 ? toSoloFilter(selection.filter) : {} }
}

/** The body fields of POST /rooms for a music choice (none = whole catalog). */
export function musicRequest(selection: MusicSelection): {
  playlistId?: string
  filter?: ReturnType<typeof toSoloFilter>
} {
  if (selection.source === 'playlist') {
    return selection.playlistId ? { playlistId: selection.playlistId } : {}
  }
  return countFilterValues(selection.filter) > 0 ? { filter: toSoloFilter(selection.filter) } : {}
}
