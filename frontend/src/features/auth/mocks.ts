import { http, HttpResponse } from 'msw'
import type { components } from '@/shared/api/schema.gen'
import guestExample from '../../../../contracts/examples/identity/create-guest.201.json'
import registeredExample from '../../../../contracts/examples/identity/get-me.registered.200.json'
import linkOauthExample from '../../../../contracts/examples/identity/link-oauth.200.json'
import unauthorizedExample from '../../../../contracts/examples/common/problem.unauthorized.401.json'
import validationExample from '../../../../contracts/examples/common/problem.validation-failed.400.json'

type Me = components['schemas']['Me']
type AuthSession = components['schemas']['AuthSession']

const PROBLEM = { 'Content-Type': 'application/problem+json' }
/** Stands in for the server's banned-word list (BR-22) in mock mode only. */
const MOCK_BANNED_WORD = 'badword'

// The mock server's "refresh cookie": the guest created last. Lost on reload, like the token.
let mockUser: Me | null = null
let issued = 0

/** For tests: forget the mock guest. */
export function resetAuthMock(): void {
  mockUser = null
  issued = 0
}

function sessionFor(user: Me): AuthSession {
  issued++
  return {
    accessToken: `mock-access-token-${String(issued)}`,
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    user,
  }
}

function isAcceptable(nickname: unknown): nickname is string {
  return (
    typeof nickname === 'string' &&
    nickname.length >= 3 &&
    nickname.length <= 20 &&
    !nickname.toLowerCase().includes(MOCK_BANNED_WORD)
  )
}

/** Auth endpoints answered from contracts/examples/identity/**. */
export const authHandlers = [
  http.post('*/api/v1/auth/guest', async ({ request }) => {
    const body: unknown = await request.json()
    const nickname =
      typeof body === 'object' && body !== null && 'nickname' in body ? body.nickname : undefined
    if (!isAcceptable(nickname)) {
      return HttpResponse.json(validationExample, { status: 400, headers: PROBLEM })
    }
    mockUser = { ...(guestExample.user as Me), nickname }
    return HttpResponse.json(sessionFor(mockUser), { status: 201 })
  }),
  http.post('*/api/v1/auth/refresh', () =>
    mockUser
      ? HttpResponse.json(sessionFor(mockUser))
      : HttpResponse.json(unauthorizedExample, { status: 401, headers: PROBLEM }),
  ),
  http.post('*/api/v1/auth/logout', () => {
    mockUser = null
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('*/api/v1/me', () =>
    mockUser
      ? HttpResponse.json(mockUser)
      : HttpResponse.json(unauthorizedExample, { status: 401, headers: PROBLEM }),
  ),
  // OAuth link endpoint: in mock mode, upgrade the user to a registered account.
  http.post('*/api/v1/auth/oauth2/:provider/link', () => {
    mockUser = registeredExample as Me
    return HttpResponse.json(linkOauthExample)
  }),
]
