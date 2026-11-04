export interface RoomChatProps {
  roomId: string
}

/**
 * Placeholder for the room chat (FE-CHAT-01 replaces the body, keeping this name and these props).
 * Screens that show a chat (lobby, match) mount it already, so they do not change later.
 */
export const RoomChat: (props: RoomChatProps) => null = () => null
