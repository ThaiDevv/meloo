import { describe, expect, it } from 'vitest'
import { isTokenExpired, resolveBrokerUrl } from './stompClient'

function jwt(payload: object): string {
  return `h.${btoa(JSON.stringify(payload))}.s`
}

describe('resolveBrokerUrl', () => {
  const https = { protocol: 'https:', host: 'meloo.example' }
  const http = { protocol: 'http:', host: 'localhost:5173' }

  it('turns a path into an absolute ws URL on the page origin', () => {
    expect(resolveBrokerUrl('/ws', https)).toBe('wss://meloo.example/ws')
    expect(resolveBrokerUrl('/ws', http)).toBe('ws://localhost:5173/ws')
  })

  it('defaults to /ws', () => {
    expect(resolveBrokerUrl(undefined, http)).toBe('ws://localhost:5173/ws')
    expect(resolveBrokerUrl('', http)).toBe('ws://localhost:5173/ws')
  })

  it('keeps an absolute URL as it is', () => {
    expect(resolveBrokerUrl('wss://api.meloo.example/ws', http)).toBe('wss://api.meloo.example/ws')
  })
})

describe('isTokenExpired', () => {
  const now = 1_800_000_000_000

  it('is true after exp and inside the 30 s leeway before it', () => {
    expect(isTokenExpired(jwt({ exp: now / 1000 - 1 }), now)).toBe(true)
    expect(isTokenExpired(jwt({ exp: now / 1000 + 10 }), now)).toBe(true)
  })

  it('is false while the token has time left', () => {
    expect(isTokenExpired(jwt({ exp: now / 1000 + 600 }), now)).toBe(false)
  })

  it('treats a token it cannot read as valid, so the server decides', () => {
    expect(isTokenExpired('opaque-token', now)).toBe(false)
    expect(isTokenExpired(jwt({ sub: 'x' }), now)).toBe(false)
    expect(isTokenExpired('a.%%%.c', now)).toBe(false)
  })
})
