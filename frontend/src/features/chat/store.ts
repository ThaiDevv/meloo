import { create } from 'zustand'
import type { ChatMessageEvent } from '@/shared/realtime/messages'

interface ChatState {
  roomId: string | null
  messages: ChatMessageEvent[]
  open: (roomId: string) => void
  receive: (roomId: string, message: ChatMessageEvent) => void
}

// Keep the current room's feed across lobby/match navigation. A new room starts empty.
export const useChatStore = create<ChatState>((set) => ({
  roomId: null,
  messages: [],
  open: (roomId) => {
    set((state) => (state.roomId === roomId ? state : { roomId, messages: [] }))
  },
  receive: (roomId, message) => {
    set((state) =>
      state.roomId !== roomId || state.messages.some((item) => item.messageId === message.messageId)
        ? state
        : { messages: [...state.messages, message].slice(-200) },
    )
  },
}))
