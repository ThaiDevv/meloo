import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { getAccessToken } from '@/shared/api/authStore'
import { DEFAULT_BASE_URL } from '@/shared/api/baseUrl'
import { refreshSession } from '@/shared/api/refresh'

/** What RoomSocket needs from a STOMP client; the real `Client` satisfies it, tests pass a fake. */
export interface StompLike {
  readonly connected: boolean
  activate: () => void
  deactivate: () => Promise<void>
  subscribe: (
    destination: string,
    callback: (message: { body: string }) => void,
  ) => { unsubscribe: () => void }
  publish: (params: { destination: string; body: string }) => void
}

export interface StompCallbacks {
  /** The broker accepted the connection (first time or after a reconnect). */
  onConnect: () => void
  /** The socket closed or the broker refused the connection; the client retries by itself. */
  onClose: () => void
}

export type StompFactory = (callbacks: StompCallbacks) => StompLike

const HEARTBEAT_MS = 10_000
const TOKEN_LEEWAY_SECONDS = 30

/** `/ws` or `wss://host/ws` to an absolute ws(s) URL: WebSocket wants one outside new browsers. */
export function resolveBrokerUrl(
  configured: string | undefined = import.meta.env.VITE_WS_URL,
  where: Pick<Location, 'protocol' | 'host'> = location,
): string {
  const target = configured && configured.length > 0 ? configured : '/ws'
  if (!target.startsWith('/')) return target
  return `${where.protocol === 'https:' ? 'wss' : 'ws'}://${where.host}${target}`
}

/** True when a JWT's `exp` is in the past (or about to be). Anything unreadable counts as valid. */
export function isTokenExpired(token: string, nowMs: number = Date.now()): boolean {
  const payload = token.split('.')[1]
  if (!payload) return false
  try {
    const json: unknown = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    if (typeof json !== 'object' || json === null || !('exp' in json)) return false
    return typeof json.exp === 'number' && json.exp * 1000 - TOKEN_LEEWAY_SECONDS * 1000 <= nowMs
  } catch {
    return false
  }
}

/** The token for the next connection attempt: refreshed first when it is missing or expired. */
async function freshAccessToken(): Promise<string | null> {
  const current = getAccessToken()
  if (current && !isTokenExpired(current)) return current
  await refreshSession(DEFAULT_BASE_URL)
  return getAccessToken()
}

/**
 * The real client. The bearer token is read again before every attempt (so a reconnect after the
 * access token rotated still gets in), the retry delay grows from 1 s up to 15 s, and a heartbeat
 * every 10 s notices a dead socket.
 */
export const createStompClient: StompFactory = (callbacks) =>
  new Client({
    brokerURL: resolveBrokerUrl(),
    reconnectDelay: 1_000,
    maxReconnectDelay: 15_000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    beforeConnect: async (client) => {
      const token = await freshAccessToken()
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
    },
    onConnect: () => {
      callbacks.onConnect()
    },
    onWebSocketClose: () => {
      callbacks.onClose()
    },
    onStompError: () => {
      // Most likely a rejected token: get a new one so the retry that follows can succeed.
      void refreshSession(DEFAULT_BASE_URL)
    },
  })
