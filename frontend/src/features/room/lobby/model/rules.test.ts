import { describe, expect, it } from 'vitest'
import {
  GUEST_ID,
  HOST_ID,
  THIRD_ID,
  host,
  player,
  quickMatchRoom,
  room,
} from '@/shared/realtime/testing/fixtures'
import { canStart, hasHost, minimumMaxPlayers, notReady, roleOf } from './rules'

describe('roleOf', () => {
  it('tells host, member and the hostless matchmaking room apart', () => {
    expect(roleOf(room(), HOST_ID)).toBe('host')
    expect(roleOf(room(), GUEST_ID)).toBe('member')
    expect(roleOf(room(), null)).toBe('member')
    expect(roleOf(quickMatchRoom(), GUEST_ID)).toBe('automatic')
    expect(hasHost(quickMatchRoom())).toBe(false)
  })
})

describe('canStart (BR-12)', () => {
  it('needs two connected players and everyone but the host ready', () => {
    expect(canStart(room({ players: [host(), player({ isReady: true })] }))).toBe(true)
    expect(canStart(room({ players: [host(), player({ isReady: false })] }))).toBe(false)
  })

  it('does not start alone', () => {
    expect(canStart(room({ players: [host()] }))).toBe(false)
  })

  it('does not wait for a disconnected player, nor count them', () => {
    const away = player({ userId: THIRD_ID, isConnected: false })
    expect(canStart(room({ players: [host(), player({ isReady: true }), away] }))).toBe(true)
    expect(canStart(room({ players: [host(), away] }))).toBe(false)
  })
})

describe('notReady', () => {
  it('lists connected, non-host players who are not ready', () => {
    const names = notReady(
      room({
        players: [
          host(),
          player({ nickname: 'A' }),
          player({ userId: THIRD_ID, nickname: 'B', isReady: true }),
          player({ userId: 'x', nickname: 'C', isConnected: false }),
        ],
      }),
    ).map((p) => p.nickname)
    expect(names).toEqual(['A'])
  })
})

describe('minimumMaxPlayers', () => {
  it('never goes below the players inside or below two', () => {
    expect(minimumMaxPlayers(room())).toBe(2)
    expect(
      minimumMaxPlayers(room({ players: [host(), player(), player({ userId: THIRD_ID })] })),
    ).toBe(3)
    expect(minimumMaxPlayers(room({ players: [host()] }))).toBe(2)
  })
})
