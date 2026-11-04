import { create } from 'zustand'
import type { ServerRealtimeMessage, RoomResponse } from './messages'
import { EMPTY_ROOM_DATA, reduceRoomMessage, type RoomData, type RoomLink } from './roomReducer'

interface RoomState extends RoomData {
  /** The room this picture belongs to; null when no screen has opened one. */
  roomId: string | null
  link: RoomLink
  /**
   * Starts (or keeps) the picture of `roomId`. A snapshot from REST seeds it so the screen has
   * something to show before the socket answers; a different room always starts empty.
   */
  open: (roomId: string, snapshot?: RoomResponse | null) => void
  setLink: (link: RoomLink) => void
  /** Feeds one server message into the picture. Messages about another room are ignored. */
  receive: (message: ServerRealtimeMessage) => void
  /** Forgets the room, e.g. after the player left it. */
  reset: () => void
}

const INITIAL = { ...EMPTY_ROOM_DATA, roomId: null, link: 'idle' as const }

export const useRoomStore = create<RoomState>((set, get) => ({
  ...INITIAL,
  open: (roomId, snapshot = null) => {
    set((state) =>
      state.roomId === roomId
        ? { room: snapshot ?? state.room }
        : { ...EMPTY_ROOM_DATA, roomId, room: snapshot, link: 'connecting' },
    )
  },
  setLink: (link) => {
    set({ link })
  },
  receive: (message) => {
    const state = get()
    // /user/queue/private is shared by every room the user is in: a snapshot of another room is noise.
    if (message.type === 'RESYNC_STATE' && message.room.roomId !== state.roomId) return
    const change = reduceRoomMessage(state, message)
    if (message.type === 'RESYNC_STATE') set({ ...change, link: 'live' })
    else if (change) set(change)
  },
  reset: () => {
    set(INITIAL)
  },
}))
