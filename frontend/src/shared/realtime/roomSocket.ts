import type { StompFactory, StompLike } from './stompClient'

/** `connecting`: never connected yet. `reconnecting`: was connected and lost it. */
export type SocketStatus = 'connecting' | 'connected' | 'reconnecting'

type Handler = (body: string) => void

interface Subscription {
  destination: string
  handler: Handler
  /** Live broker subscription; null while the socket is down. */
  live: { unsubscribe: () => void } | null
}

/**
 * One STOMP connection for one room. A subscription made here outlives reconnects: the client
 * does not re-subscribe by itself, so every (re)connect replays the list.
 */
export class RoomSocket {
  readonly roomId: string
  private readonly client: StompLike
  private readonly subscriptions = new Set<Subscription>()
  private readonly statusListeners = new Set<(status: SocketStatus) => void>()
  private readonly connectListeners = new Set<() => void>()
  private current: SocketStatus = 'connecting'
  private everConnected = false
  private closed = false

  constructor(roomId: string, factory: StompFactory) {
    this.roomId = roomId
    this.client = factory({
      onConnect: () => {
        this.handleConnect()
      },
      onClose: () => {
        this.handleClose()
      },
    })
  }

  get status(): SocketStatus {
    return this.current
  }

  start(): void {
    this.client.activate()
  }

  /** Calls `listener` after every (re)connect, once the subscriptions are back in place. */
  onConnect(listener: () => void): () => void {
    this.connectListeners.add(listener)
    return () => {
      this.connectListeners.delete(listener)
    }
  }

  onStatus(listener: (status: SocketStatus) => void): () => void {
    this.statusListeners.add(listener)
    return () => {
      this.statusListeners.delete(listener)
    }
  }

  subscribe(destination: string, handler: Handler): () => void {
    const subscription: Subscription = { destination, handler, live: null }
    this.subscriptions.add(subscription)
    if (this.client.connected) this.attach(subscription)
    return () => {
      subscription.live?.unsubscribe()
      subscription.live = null
      this.subscriptions.delete(subscription)
    }
  }

  /** False when the socket is down: the command was not sent and the caller must say so. */
  publish(destination: string, body: unknown): boolean {
    if (!this.client.connected) return false
    this.client.publish({ destination, body: JSON.stringify(body) })
    return true
  }

  close(): void {
    this.closed = true
    this.subscriptions.clear()
    this.statusListeners.clear()
    this.connectListeners.clear()
    void this.client.deactivate()
  }

  private attach(subscription: Subscription): void {
    subscription.live = this.client.subscribe(subscription.destination, (message) => {
      subscription.handler(message.body)
    })
  }

  private setStatus(next: SocketStatus): void {
    if (next === this.current) return
    this.current = next
    for (const listener of this.statusListeners) listener(next)
  }

  private handleConnect(): void {
    if (this.closed) return
    this.everConnected = true
    for (const subscription of this.subscriptions) this.attach(subscription)
    this.setStatus('connected')
    for (const listener of this.connectListeners) listener()
  }

  private handleClose(): void {
    if (this.closed) return
    for (const subscription of this.subscriptions) subscription.live = null
    this.setStatus(this.everConnected ? 'reconnecting' : 'connecting')
  }
}
