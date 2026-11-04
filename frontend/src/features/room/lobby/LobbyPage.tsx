import { useParams } from 'react-router-dom'
import { CreateRoomPage } from './create/CreateRoomPage'
import { RoomEntry } from './RoomEntry'

/**
 * One component for the two routes declared in routes.tsx: /rooms/new (create a room) and
 * /r/:code (join it and wait in its lobby).
 */
export function LobbyPage() {
  const { code } = useParams()
  return code ? <RoomEntry param={code} /> : <CreateRoomPage />
}
