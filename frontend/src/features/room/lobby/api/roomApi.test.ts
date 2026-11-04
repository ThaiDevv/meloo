import { describe, expect, it } from 'vitest'
import { failureOf, toRoom } from './roomApi'
import joinExample from '../../../../../../contracts/examples/room/join-room.200.json'
import quickExample from '../../../../../../contracts/examples/room/quick-match.200.json'

describe('failureOf', () => {
  it.each([
    [409, { code: 'ROOM_FULL' }, { reason: 'full' }],
    [403, { code: 'ROOM_KICKED' }, { reason: 'kicked' }],
    [403, { code: 'ACCOUNT_LOCKED' }, { reason: 'locked' }],
    [404, { code: 'ROOM_NOT_FOUND' }, { reason: 'not-found' }],
    [404, { code: 'PLAYLIST_NOT_FOUND' }, { reason: 'playlist-not-found' }],
    [
      422,
      { code: 'NOT_ENOUGH_TRACKS', missingTracks: 4 },
      { reason: 'not-enough-tracks', missingTracks: 4 },
    ],
    [401, { code: 'UNAUTHORIZED' }, { reason: 'unauthorized' }],
    [429, { code: 'RATE_LIMITED' }, { reason: 'rate-limited' }],
    [400, { code: 'VALIDATION_FAILED' }, { reason: 'rejected' }],
    [500, { code: 'INTERNAL_ERROR' }, { reason: 'unknown' }],
  ])('maps %i %j', (status, body, failure) => {
    expect(failureOf(status, body)).toEqual(failure)
  })

  it('falls back on the status when the body has no code', () => {
    expect(failureOf(409, undefined)).toEqual({ reason: 'full' })
    expect(failureOf(404, 'plain text')).toEqual({ reason: 'not-found' })
    expect(failureOf(403, null)).toEqual({ reason: 'unknown' })
  })
})

// JSON imports widen the enums to string, so the few enum fields are pinned back to the contract's values.
const quick = {
  ...quickExample,
  roomType: 'QUICK_MATCH' as const,
  status: 'LOBBY' as const,
  roundCount: 10 as const,
}
const joined = {
  ...joinExample,
  roomType: 'PRIVATE_ROOM' as const,
  status: 'LOBBY' as const,
  roundCount: 10 as const,
}

describe('toRoom', () => {
  it('spells out the nulls the REST payload leaves out', () => {
    const room = toRoom(quick)
    expect(room).toMatchObject({ code: null, hostId: null, roomType: 'QUICK_MATCH' })
    expect(room.players.every((p) => p.avatarUrl === null || typeof p.avatarUrl === 'string')).toBe(
      true,
    )
  })

  it('keeps a private room as the server sent it', () => {
    const room = toRoom(joined)
    expect(room.code).toBe('MEL889')
    expect(room.playerCount).toBe(joinExample.players.length)
    expect(room.filter).toBeNull()
  })
})
