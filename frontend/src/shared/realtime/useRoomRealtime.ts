import { useCallback, useEffect, useRef } from 'react'
import { commandDestination, topicDestination, type RoomCommands, type RoomTopic } from './commands'
import type { RoomResponse, ServerRealtimeMessage } from './messages'
import { parseServerMessage } from './parse'
import { acquireRoomSocket } from './roomConnection'
import type { RoomSocket } from './roomSocket'
import type { RoomLink } from './roomReducer'
import { useRoomStore } from './roomStore'

interface Options {
  /** A snapshot the caller already has (the REST response of create/join), shown until the socket answers. */
  initialRoom?: RoomResponse | null
  /**
   * Extra topics to listen to (chat, scoreboard, rtc...). The lobby and round topics already feed
   * the room store for every screen, so listing them here is only for `onMessage`.
   */
  topics?: readonly RoomTopic[]
  /** Called for each message of `topics`. May change between renders without resubscribing. */
  onMessage?: (message: ServerRealtimeMessage) => void
}

export interface RoomRealtime {
  /** `live` means a snapshot arrived on the current connection; anything else deserves a banner. */
  link: RoomLink
  /** Sends a command. False when the socket is down: nothing was sent. */
  send: <K extends keyof RoomCommands>(command: K, body: RoomCommands[K]) => boolean
}

const NO_TOPICS: readonly RoomTopic[] = []

/**
 * Connects the screen to a room over STOMP and keeps the room store current. Reconnects by
 * itself; every screen of the same room shares one connection. Chat, match and voice use the
 * same hook with `topics` and `onMessage`.
 */
export function useRoomRealtime(
  roomId: string | null,
  { initialRoom = null, topics = NO_TOPICS, onMessage }: Options = {},
): RoomRealtime {
  const link = useRoomStore((state) => state.link)
  const socketRef = useRef<RoomSocket | null>(null)
  const onMessageRef = useRef(onMessage)
  const topicsRef = useRef(topics)
  const initialRoomRef = useRef(initialRoom)
  const topicKey = topics.join(',')

  useEffect(() => {
    onMessageRef.current = onMessage
    topicsRef.current = topics
    initialRoomRef.current = initialRoom
  })

  useEffect(() => {
    if (!roomId) return undefined
    useRoomStore.getState().open(roomId, initialRoomRef.current)
    const { socket, release } = acquireRoomSocket(roomId)
    socketRef.current = socket
    const unsubscribers = topicsRef.current.map((topic) =>
      socket.subscribe(topicDestination(roomId, topic), (body) => {
        const message = parseServerMessage(body)
        if (message) onMessageRef.current?.(message)
      }),
    )
    return () => {
      for (const off of unsubscribers) off()
      socketRef.current = null
      release()
    }
  }, [roomId, topicKey])

  const send = useCallback(
    <K extends keyof RoomCommands>(command: K, body: RoomCommands[K]): boolean => {
      const socket = socketRef.current
      if (!socket || !roomId) return false
      return socket.publish(commandDestination(roomId, command), body)
    },
    [roomId],
  )

  return { link, send }
}
