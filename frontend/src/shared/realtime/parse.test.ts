import { describe, expect, it } from 'vitest'
import { parseServerMessage } from './parse'

describe('parseServerMessage', () => {
  it('returns a message whose type the contract knows', () => {
    const body = JSON.stringify({
      type: 'PLAYER_READY_CHANGED',
      userId: 'u',
      isReady: true,
      serverTime: 't',
    })
    expect(parseServerMessage(body)).toMatchObject({ type: 'PLAYER_READY_CHANGED', isReady: true })
  })

  it.each([
    ['not JSON', 'not json'],
    ['a JSON string', '"PLAYER_JOINED"'],
    ['null', 'null'],
    ['an object without type', '{"a":1}'],
    ['an unknown type', '{"type":"SOMETHING_ELSE"}'],
    ['a numeric type', '{"type":3}'],
  ])('drops %s', (_name, body) => {
    expect(parseServerMessage(body)).toBeNull()
  })
})
