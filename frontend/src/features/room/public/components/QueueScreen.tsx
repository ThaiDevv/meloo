import { Link2, Loader } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useRoomRealtime, useRoomStore } from '@/shared/realtime'
import type { RoomResponse } from '@/shared/realtime/messages'
import { Button } from '@/shared/ui/Button'
import { toast } from '@/shared/ui/toast-store'
import { useCreateRoom } from '../../lobby/api/roomApi'
import { ConnectionBanner } from '../../lobby/components/ConnectionBanner'
import { musicRequest, selectionOf } from '../../lobby/model/music'
import { inviteLink } from '../../lobby/model/roomCode'
import { failureOfError } from '../api/publicRoomsApi'
import { failureText } from '../model/errorText'
import { ALONE_HINT_AFTER_S, formatElapsed } from '../model/waiting'

interface QueueScreenProps {
  /** The room matchmaking put the player in: they are already inside it. */
  room: RoomResponse
  /** Back to choosing music, after the player left the queue. */
  onLeft: () => void
}

/**
 * Waiting for opponents in a matchmaking room: how long it has taken, a 30 s countdown once a second
 * player is in, and after 60 s alone two other ways to play (BR-13). The player can leave any time.
 */
export function QueueScreen({ room: initialRoom, onLeft }: QueueScreenProps) {
  const { t } = useTranslation('room')
  const navigate = useNavigate()
  const createRoom = useCreateRoom()
  const { link, send } = useRoomRealtime(initialRoom.roomId, { initialRoom })
  const stored = useRoomStore((state) => state.room)
  const countdown = useRoomStore((state) => state.countdown)
  const soloSuggestion = useRoomStore((state) => state.soloSuggestion)
  const matchStarting = useRoomStore((state) => state.matchStarting)
  const roundPrepareCount = useRoomStore((state) => state.roundPrepareCount)
  const [elapsed, setElapsed] = useState(0)
  const [failure, setFailure] = useState<string | null>(null)

  const room = stored?.roomId === initialRoom.roomId ? stored : initialRoom
  const players = room.players.length
  const counting = countdown !== null
  const alone = !counting && players < 2

  useEffect(() => {
    const timer = setInterval(() => {
      setElapsed((seconds) => seconds + 1)
    }, 1000)
    return () => {
      clearInterval(timer)
    }
  }, [])

  // The first round is prepared once the countdown ran out: the match page takes over from here.
  const seenRounds = useRef(roundPrepareCount)
  useEffect(() => {
    if (roundPrepareCount > seenRounds.current && matchStarting) {
      void navigate(`/r/${room.roomId}/play`, { state: { room } })
    }
    seenRounds.current = roundPrepareCount
  }, [roundPrepareCount, matchStarting, navigate, room])

  const leaveQueue = () => {
    send('leave', {})
    useRoomStore.getState().reset()
  }
  const cancel = () => {
    leaveQueue()
    onLeft()
  }
  const playSolo = () => {
    leaveQueue()
    void navigate('/solo')
  }
  const inviteFriends = () => {
    setFailure(null)
    createRoom.mutate(
      {
        roomType: 'PRIVATE_ROOM',
        roundCount: room.roundCount,
        maxPlayers: room.maxPlayers,
        ...musicRequest(selectionOf(room)),
      },
      {
        onSuccess: (privateRoom) => {
          leaveQueue()
          const code = privateRoom.code ?? privateRoom.roomId
          if (privateRoom.code) {
            void navigator.clipboard.writeText(inviteLink(privateRoom.code)).then(
              () => {
                toast({ title: t('code.copiedLink'), tone: 'positive' })
              },
              () => {
                toast({
                  title: t('code.copyFailed'),
                  description: t('code.copyFailedHint'),
                  tone: 'negative',
                })
              },
            )
          }
          void navigate(`/r/${code}`, { state: { room: privateRoom } })
        },
        onError: (error) => {
          setFailure(failureText(failureOfError(error), t))
        },
      },
    )
  }

  const showHint = alone && (elapsed >= ALONE_HINT_AFTER_S || soloSuggestion !== null)

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-10 lg:py-14">
      <div className="space-y-4 empty:hidden">
        <ConnectionBanner link={link} />
      </div>
      <section
        aria-labelledby="queue-heading"
        className="mt-4 space-y-8 rounded-2xl bg-primary p-8 text-primary-foreground"
      >
        {counting ? (
          <div className="space-y-3">
            <h1 id="queue-heading" className="text-base font-semibold opacity-80">
              {t('quick.found')}
            </h1>
            <p
              role="timer"
              className="num font-display text-[clamp(4.5rem,20vw,7rem)] leading-none font-extrabold"
            >
              {countdown}
              <span className="ml-3 text-2xl font-semibold opacity-80">{t('countdown.unit')}</span>
            </p>
            <p className="text-sm opacity-80">
              {t('quick.players', { n: players, max: room.maxPlayers })}
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            <h1
              id="queue-heading"
              className="flex items-center gap-3 font-display text-4xl leading-tight font-extrabold tracking-[-0.02em]"
            >
              <Loader aria-hidden="true" className="size-7 animate-spin" />
              {t('quick.searching')}
            </h1>
            <p className="text-base">
              {t('quick.waited')}{' '}
              <span className="num font-semibold" data-testid="queue-elapsed">
                {formatElapsed(elapsed)}
              </span>
            </p>
            <p className="text-sm opacity-80">
              {t('quick.players', { n: players, max: room.maxPlayers })}
            </p>
          </div>
        )}
        <Button
          variant="outline"
          className="bg-transparent text-primary-foreground focus-visible:outline-primary-foreground"
          onClick={cancel}
        >
          {t('quick.cancel')}
        </Button>
      </section>

      {showHint ? (
        <section
          aria-labelledby="alone-heading"
          className="mt-6 space-y-4 rounded-2xl bg-raised p-6"
        >
          <h2 id="alone-heading" className="text-lg font-semibold">
            {t('quick.alone.title')}
          </h2>
          <p className="text-sm text-muted-foreground">{t('quick.alone.description')}</p>
          <div className="flex flex-wrap gap-3">
            <Button onClick={playSolo}>{t('quick.alone.solo')}</Button>
            <Button variant="outline" disabled={createRoom.isPending} onClick={inviteFriends}>
              <Link2 aria-hidden="true" className="size-4" />
              {createRoom.isPending ? t('create.submitting') : t('quick.alone.invite')}
            </Button>
          </div>
          <p className="text-sm text-muted-foreground">{t('quick.alone.unranked')}</p>
          <p className="text-sm">{t('quick.alone.keepWaiting')}</p>
          {failure ? (
            <p role="alert" className="rounded-xl bg-negative-soft p-4 text-sm text-negative">
              {failure}
            </p>
          ) : null}
        </section>
      ) : null}
    </div>
  )
}
