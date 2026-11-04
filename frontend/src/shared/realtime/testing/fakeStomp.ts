import { closeAllRoomSockets, setStompFactory } from '../roomConnection'
import type { StompCallbacks, StompLike } from '../stompClient'

interface Sub {
  destination: string
  callback: (message: { body: string }) => void
}

/**
 * A STOMP client that never touches the network, for tests of every screen that uses
 * `useRoomRealtime`. The test drives it: `connect()`, `drop()`, `emit()`.
 */
export class FakeStomp implements StompLike {
  connected = false
  activated = false
  deactivated = false
  readonly published: { destination: string; body: unknown }[] = []
  private readonly subs = new Set<Sub>()
  private readonly callbacks: StompCallbacks

  constructor(callbacks: StompCallbacks) {
    this.callbacks = callbacks
  }

  activate(): void {
    this.activated = true
  }

  deactivate(): Promise<void> {
    this.deactivated = true
    this.connected = false
    this.subs.clear()
    return Promise.resolve()
  }

  subscribe(destination: string, callback: (message: { body: string }) => void) {
    const sub: Sub = { destination, callback }
    this.subs.add(sub)
    return {
      unsubscribe: () => {
        this.subs.delete(sub)
      },
    }
  }

  publish(params: { destination: string; body: string }): void {
    const body: unknown = JSON.parse(params.body)
    this.published.push({ destination: params.destination, body })
  }

  /** The broker accepts the connection. */
  connect(): void {
    this.connected = true
    this.callbacks.onConnect()
  }

  /** The socket dies: the broker forgets every subscription. */
  drop(): void {
    this.connected = false
    this.subs.clear()
    this.callbacks.onClose()
  }

  /** The server sends `message` to everyone subscribed to `destination`. */
  emit(destination: string, message: unknown): void {
    for (const sub of [...this.subs]) {
      if (sub.destination === destination) sub.callback({ body: JSON.stringify(message) })
    }
  }

  isSubscribed(destination: string): boolean {
    return [...this.subs].some((sub) => sub.destination === destination)
  }

  /** Bodies of the commands sent under this name ("ready", "resync"...), oldest first. */
  commands(command: string): unknown[] {
    return this.published
      .filter((item) => item.destination.endsWith(`/${command}`))
      .map((item) => item.body)
  }
}

export interface FakeStompKit {
  clients: FakeStomp[]
  /** The most recently created client; throws if none exists yet. */
  latest: () => FakeStomp
  uninstall: () => void
}

/** Replaces the real STOMP client until `uninstall()`. Call it in beforeEach, uninstall in afterEach. */
export function installFakeStomp(): FakeStompKit {
  const clients: FakeStomp[] = []
  setStompFactory((callbacks) => {
    const client = new FakeStomp(callbacks)
    clients.push(client)
    return client
  })
  return {
    clients,
    latest: () => {
      const client = clients.at(-1)
      if (!client) throw new Error('No STOMP client was created yet')
      return client
    },
    uninstall: () => {
      closeAllRoomSockets()
      setStompFactory()
    },
  }
}
