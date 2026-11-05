import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { useRoomRealtime, useRoomStore } from '@/shared/realtime'
import type { RoomResponse } from '@/shared/realtime/messages'
import { RoomRequestError, useJoinRoom } from '../lobby/api/roomApi'
import { JoiningSkeleton } from '../lobby/components/JoiningSkeleton'
import { RoomProblem, type RoomProblemKind } from '../lobby/components/RoomProblem'
import { readRoomCode } from '../lobby/model/roomCode'
import { LiveMatch } from './LiveMatch'

function seededRoom(state: unknown): RoomResponse | null {
  if (typeof state !== 'object' || state === null || !('room' in state)) return null
  const { room } = state
  return typeof room === 'object' &&
    room !== null &&
    'roomId' in room &&
    typeof room.roomId === 'string' &&
    'players' in room &&
    Array.isArray(room.players)
    ? (room as RoomResponse)
    : null
}

/** /r/:code/play. Comes from the lobby with the room at hand; on a reload it joins by code first. */
export function MatchPage() {
  const { code = '' } = useParams()
  const location = useLocation()
  const stored = useRoomStore((s) => s.room)
  const wanted = readRoomCode(code)
  const known =
    stored && (stored.roomId === code || (wanted !== null && stored.code === wanted))
      ? stored
      : seededRoom(location.state)
  if (known) return <LiveMatch initialRoom={known} />
  // A quick match has no code: its link carries the room id, and the room comes from the server's snapshot.
  if (wanted === null && ROOM_ID_PATTERN.test(code)) return <ResyncThenMatch roomId={code} />
  return <JoinThenMatch code={wanted} />
}

const ROOM_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * Reload of a room without a code: connect to the room by its id and wait for the snapshot. Once the
 * store holds the room, MatchPage renders the match; if the server refuses, the room is not the player's.
 */
function ResyncThenMatch({ roomId }: { roomId: string }) {
  useRoomRealtime(roomId)
  const lastError = useRoomStore((s) => s.lastError)
  if (lastError) return <RoomProblem kind="not-found" />
  return <JoiningSkeleton />
}

function JoinThenMatch({ code }: { code: string | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { mutate, data, error } = useJoinRoom()
  const [attempt, setAttempt] = useState(0)
  const started = useRef<string | null>(null)
  const failure = error instanceof RoomRequestError ? error.failure : null

  useEffect(() => {
    if (!code) return
    const key = `${code}:${String(attempt)}`
    if (started.current === key) return
    started.current = key
    mutate(code)
  }, [code, attempt, mutate])

  useEffect(() => {
    if (failure?.reason === 'unauthorized') {
      void navigate('/start', { replace: true, state: { from: location.pathname } })
    }
  }, [failure, navigate, location.pathname])

  if (!code) return <RoomProblem kind="invalid-code" />
  if (data) return <LiveMatch initialRoom={data} />
  if (error && failure?.reason !== 'unauthorized') {
    const kind: RoomProblemKind =
      failure &&
      (failure.reason === 'full' ||
        failure.reason === 'kicked' ||
        failure.reason === 'locked' ||
        failure.reason === 'not-found' ||
        failure.reason === 'rate-limited')
        ? failure.reason
        : 'unknown'
    return (
      <RoomProblem
        kind={kind}
        onRetry={() => {
          setAttempt((n) => n + 1)
        }}
      />
    )
  }
  return <JoiningSkeleton />
}
