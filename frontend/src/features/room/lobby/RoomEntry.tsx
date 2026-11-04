import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import type { RoomResponse } from '@/shared/realtime/messages'
import { RoomRequestError, useJoinRoom, type RoomFailure } from './api/roomApi'
import { JoiningSkeleton } from './components/JoiningSkeleton'
import { RoomProblem, type RoomProblemKind } from './components/RoomProblem'
import { ConnectedLobby } from './ConnectedLobby'
import { readRoomCode } from './model/roomCode'

function isRoomSnapshot(value: unknown): value is RoomResponse {
  return (
    typeof value === 'object' &&
    value !== null &&
    'roomId' in value &&
    typeof value.roomId === 'string' &&
    'players' in value &&
    Array.isArray(value.players)
  )
}

/**
 * A room handed over by the screen that just put the player in it (room creation, matchmaking):
 * they are already inside, so joining again would only fail or double up. It must be this room.
 */
function seededRoom(state: unknown, param: string, code: string | null): RoomResponse | null {
  if (typeof state !== 'object' || state === null || !('room' in state)) return null
  const room = state.room
  if (!isRoomSnapshot(room)) return null
  return room.roomId === param || (code !== null && room.code === code) ? room : null
}

function problemKind(reason: RoomFailure['reason']): RoomProblemKind {
  switch (reason) {
    case 'full':
    case 'kicked':
    case 'locked':
    case 'not-found':
    case 'rate-limited':
      return reason
    default:
      return 'unknown'
  }
}

/** /r/:code: joins the room by its code, then shows the lobby or why the player cannot be in it. */
export function RoomEntry({ param }: { param: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const code = readRoomCode(param)
  const seeded = seededRoom(location.state, param, code)
  const { mutate, data, error } = useJoinRoom()
  const [attempt, setAttempt] = useState(0)
  const started = useRef<string | null>(null)
  const failure = error instanceof RoomRequestError ? error.failure : null

  useEffect(() => {
    if (seeded || !code) return
    const key = `${code}:${String(attempt)}`
    // React runs effects twice in development; the ref keeps that from joining twice.
    if (started.current === key) return
    started.current = key
    mutate(code)
  }, [seeded, code, attempt, mutate])

  useEffect(() => {
    if (failure?.reason === 'unauthorized') {
      void navigate('/start', { replace: true, state: { from: location.pathname } })
    }
  }, [failure, navigate, location.pathname])

  if (seeded) return <ConnectedLobby initialRoom={seeded} />
  if (!code) return <RoomProblem kind="invalid-code" />
  if (data) return <ConnectedLobby initialRoom={data} />
  if (error && failure?.reason !== 'unauthorized') {
    return (
      <RoomProblem
        kind={failure ? problemKind(failure.reason) : 'unknown'}
        onRetry={() => {
          setAttempt((n) => n + 1)
        }}
      />
    )
  }
  return <JoiningSkeleton />
}
