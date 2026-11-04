import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { RoomChat } from '@/features/chat'
import { useAuthStore } from '@/shared/api/authStore'
import { useRoomRealtime, useRoomStore } from '@/shared/realtime'
import type { RoomPlayerSummary, RoomResponse, SettingsCommand } from '@/shared/realtime/messages'
import { Badge } from '@/shared/ui/Badge'
import { ConnectionBanner } from './components/ConnectionBanner'
import { KickDialog } from './components/KickDialog'
import { LobbyActions } from './components/LobbyActions'
import { LobbyAlert } from './components/LobbyAlert'
import { MatchCountdown, SoloHint } from './components/MatchCountdown'
import { PlayerList } from './components/PlayerList'
import { RoomCode } from './components/RoomCode'
import { SettingsPanel } from './components/SettingsPanel'
import { errorText } from './model/errorText'
import { canStart, hasHost, notReady, roleOf } from './model/rules'

/** How long the host waits for the server to answer "start" before being told it did not. */
const START_REPLY_MS = 5_000

const ROOM_TYPE_KEY = {
  PRIVATE_ROOM: 'lobby.type.private',
  PUBLIC_ROOM: 'lobby.type.public',
  QUICK_MATCH: 'lobby.type.quick',
} as const

/** The lobby of one room, live: connects, follows the room and sends the player's commands. */
export function LiveLobby({ initialRoom }: { initialRoom: RoomResponse }) {
  const { t } = useTranslation('room')
  const navigate = useNavigate()
  const me = useAuthStore((state) => state.user?.id ?? null)
  const { link, send } = useRoomRealtime(initialRoom.roomId, { initialRoom })

  const stored = useRoomStore((state) => state.room)
  const countdown = useRoomStore((state) => state.countdown)
  const stoppedReason = useRoomStore((state) => state.countdownStoppedReason)
  const soloSuggestion = useRoomStore((state) => state.soloSuggestion)
  const waitingPlayers = useRoomStore((state) => state.waitingPlayers)
  const matchStarting = useRoomStore((state) => state.matchStarting)
  const roundPrepareCount = useRoomStore((state) => state.roundPrepareCount)
  const lastError = useRoomStore((state) => state.lastError)
  const dismissError = useRoomStore((state) => state.dismissError)
  const [kicking, setKicking] = useState<RoomPlayerSummary | null>(null)
  const [startedAt, setStartedAt] = useState<number | null>(null)
  const [startNoReply, setStartNoReply] = useState(false)

  // The store may still hold another room for one render, before the hook opens this one.
  const room = stored?.roomId === initialRoom.roomId ? stored : initialRoom
  const role = roleOf(room, me)
  // While a match runs even the host only waits here (BR-15): no start, no ready.
  const actionRole = room.status === 'PLAYING' && role === 'host' ? 'member' : role
  const self = room.players.find((player) => player.userId === me) ?? null
  const inProgress = room.status === 'PLAYING'
  const counting = countdown !== null

  // Go to the match when its first round is prepared, but only if this player saw it start: someone
  // who joined mid-match (BR-15) waits here for the next one.
  const seenRounds = useRef(roundPrepareCount)
  useEffect(() => {
    if (roundPrepareCount > seenRounds.current && matchStarting) {
      void navigate(`/r/${room.code ?? room.roomId}/play`)
    }
    seenRounds.current = roundPrepareCount
  }, [roundPrepareCount, matchStarting, navigate, room.code, room.roomId])

  // "Start" is answered by a countdown or an error. Neither within a few seconds means the server did
  // not act on it (for instance it does not handle the command yet): say so instead of staying silent.
  useEffect(() => {
    if (startedAt === null || counting) return undefined
    const timer = setTimeout(() => {
      setStartNoReply(true)
      setStartedAt(null)
    }, START_REPLY_MS)
    return () => {
      clearTimeout(timer)
    }
  }, [startedAt, counting])
  const noReply = startNoReply && !counting && !lastError
  const dismiss = () => {
    dismissError()
    setStartNoReply(false)
  }

  const canSend = link === 'live' || link === 'syncing'
  const commandsEnabled = canSend && !counting && !inProgress

  const leave = () => {
    send('leave', {})
    useRoomStore.getState().reset()
    // Forget the snapshot kept in this history entry, so "back" joins again instead of replaying it.
    void navigate('.', { replace: true, state: null })
    void navigate('/')
  }
  const changeSettings = (settings: SettingsCommand) => {
    dismissError()
    send('settings', settings)
  }

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 lg:py-12">
      <header className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <h1 className="font-display text-4xl leading-none font-extrabold tracking-[-0.03em] sm:text-5xl">
          {t('lobby.title')}
        </h1>
        <Badge>{t(ROOM_TYPE_KEY[room.roomType])}</Badge>
      </header>

      <div className="mt-4 space-y-3 empty:hidden">
        <ConnectionBanner link={link} />
        {inProgress ? (
          <p role="status" className="rounded-xl bg-raised px-4 py-3 text-sm">
            {t('lobby.inProgress')}
          </p>
        ) : null}
        {noReply ? <LobbyAlert text={t('errors.startNoReply')} onDismiss={dismiss} /> : null}
        {lastError ? <LobbyAlert text={errorText(lastError, t)} onDismiss={dismiss} /> : null}
      </div>

      <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_28rem] lg:items-start lg:gap-12">
        <div className="space-y-8">
          <section aria-labelledby="players-heading" className="space-y-4">
            <h2
              id="players-heading"
              className="font-display text-2xl font-extrabold tracking-[-0.02em]"
            >
              {t('players.heading', { n: room.players.length, max: room.maxPlayers })}
            </h2>
            <PlayerList
              players={room.players}
              maxPlayers={room.maxPlayers}
              me={me}
              showReady={hasHost(room) && !inProgress}
              onKick={role === 'host' && !counting && !inProgress ? setKicking : undefined}
            />
          </section>

          {waitingPlayers.length > 0 ? (
            <section aria-labelledby="waiting-heading" className="space-y-2">
              <h2 id="waiting-heading" className="text-base font-semibold">
                {t('players.waiting', { n: waitingPlayers.length })}
              </h2>
              <p className="text-sm text-muted-foreground">
                {waitingPlayers.map((player) => player.nickname).join(', ')}
              </p>
            </section>
          ) : null}

          <RoomChat roomId={room.roomId} />
        </div>

        <aside aria-label={t('lobby.sidebar')} className="space-y-6 lg:sticky lg:top-20">
          {counting || role === 'automatic' || room.code ? (
            <div className="space-y-8 rounded-2xl bg-primary p-6 text-primary-foreground">
              <MatchCountdown
                seconds={countdown}
                stoppedReason={stoppedReason}
                matchmaking={
                  role === 'automatic'
                    ? { players: room.players.length, max: room.maxPlayers }
                    : undefined
                }
              />
              {role !== 'automatic' && room.code ? <RoomCode code={room.code} /> : null}
            </div>
          ) : null}
          {role === 'automatic' && soloSuggestion ? <SoloHint /> : null}
          <div className="space-y-8 rounded-2xl bg-surface p-5">
            <LobbyActions
              role={actionRole}
              self={inProgress ? null : self}
              canStart={canStart(room)}
              waitingFor={notReady(room).map((player) => player.nickname)}
              playerCount={room.players.filter((player) => player.isConnected).length}
              enabled={commandsEnabled}
              onReady={(ready) => {
                dismissError()
                send('ready', { ready })
              }}
              onStart={() => {
                dismiss()
                if (send('start', {})) setStartedAt(Date.now())
                else setStartNoReply(true)
              }}
              onLeave={leave}
            />
            <SettingsPanel
              room={room}
              editable={role === 'host' && !counting && !inProgress}
              canSend={canSend}
              onChange={changeSettings}
            />
          </div>
        </aside>
      </div>

      <KickDialog
        player={kicking}
        onCancel={() => {
          setKicking(null)
        }}
        onConfirm={(player) => {
          send('kick', { targetUserId: player.userId })
          setKicking(null)
        }}
      />
    </div>
  )
}
