import { commandDestination, ERRORS_QUEUE, PRIVATE_QUEUE, topicDestination } from './commands'
import { parseServerMessage } from './parse'
import { useRoomStore } from './roomStore'
import { RoomSocket, type SocketStatus } from './roomSocket'
import { createStompClient, type StompFactory } from './stompClient'

/** How long to wait for RESYNC_STATE before asking again, and how many times to ask. */
const RESYNC_RETRY_MS = 4_000
const RESYNC_MAX_TRIES = 3

let factory: StompFactory = createStompClient

/** Test seam: swaps the STOMP client for a fake. Pass nothing to restore the real one. */
export function setStompFactory(next: StompFactory = createStompClient): void {
  factory = next
}

interface Entry {
  socket: RoomSocket
  refs: number
  teardown: () => void
  idleTimer: ReturnType<typeof setTimeout> | null
}

const entries = new Map<string, Entry>()

function linkOf(status: SocketStatus): 'connecting' | 'syncing' | 'reconnecting' {
  if (status === 'connected') return 'syncing'
  return status
}

/**
 * Opens the connection of a room and wires it to the room store: lobby and round topics, the
 * player's private queues, and a RESYNC request after every (re)connect (the server answers with
 * the whole room, which is what makes a reconnect safe). Returns what undoes it all.
 */
function wire(socket: RoomSocket): () => void {
  const { roomId } = socket
  const store = useRoomStore.getState()
  const feed = (body: string) => {
    const message = parseServerMessage(body)
    if (message) useRoomStore.getState().receive(message)
  }
  const unsubscribers = [
    socket.subscribe(topicDestination(roomId, 'lobby'), feed),
    socket.subscribe(topicDestination(roomId, 'round'), feed),
    socket.subscribe(ERRORS_QUEUE, feed),
    socket.subscribe(PRIVATE_QUEUE, feed),
  ]

  let retry: ReturnType<typeof setTimeout> | null = null
  const stopRetry = () => {
    if (retry) clearTimeout(retry)
    retry = null
  }
  const askForSnapshot = (tries: number) => {
    stopRetry()
    socket.publish(commandDestination(roomId, 'resync'), {})
    if (tries >= RESYNC_MAX_TRIES) return
    retry = setTimeout(() => {
      if (useRoomStore.getState().link === 'syncing') askForSnapshot(tries + 1)
    }, RESYNC_RETRY_MS)
  }

  const offStatus = socket.onStatus((status) => {
    // Keep "live" out of this mapping: only a snapshot may say the picture is current.
    useRoomStore.getState().setLink(linkOf(status))
    if (status !== 'connected') stopRetry()
  })
  const offConnect = socket.onConnect(() => {
    askForSnapshot(1)
  })
  store.setLink(linkOf(socket.status))
  socket.start()

  return () => {
    stopRetry()
    offStatus()
    offConnect()
    for (const off of unsubscribers) off()
    socket.close()
  }
}

/**
 * Shares one connection per room between every screen that needs it (lobby, match, chat, voice):
 * the first caller opens it, the last one to release closes it. Closing waits one tick so React's
 * unmount-then-remount (StrictMode, route changes inside the same room) keeps the socket.
 */
export function acquireRoomSocket(roomId: string): { socket: RoomSocket; release: () => void } {
  let entry = entries.get(roomId)
  if (entry) {
    if (entry.idleTimer) clearTimeout(entry.idleTimer)
    entry.idleTimer = null
  } else {
    const socket = new RoomSocket(roomId, factory)
    entry = { socket, refs: 0, teardown: wire(socket), idleTimer: null }
    entries.set(roomId, entry)
  }
  entry.refs += 1
  const held = entry
  let released = false
  return {
    socket: held.socket,
    release: () => {
      if (released) return
      released = true
      held.refs -= 1
      if (held.refs > 0) return
      held.idleTimer = setTimeout(() => {
        if (held.refs > 0 || entries.get(roomId) !== held) return
        entries.delete(roomId)
        held.teardown()
      }, 0)
    },
  }
}

/** Test helper: closes every connection at once. */
export function closeAllRoomSockets(): void {
  for (const [roomId, entry] of entries) {
    if (entry.idleTimer) clearTimeout(entry.idleTimer)
    entries.delete(roomId)
    entry.teardown()
  }
}
