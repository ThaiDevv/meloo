import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { RoomChat } from '@/features/chat'
import { useAchievementNotifications } from '@/features/profile/achievements'
import { GameRound, RoundReveal } from '@/features/game-core'
import { SnippetPlayer } from '@/features/game-core/audio/snippetPlayer'
import type { RoundResult, RoundState } from '@/features/game-core'
import { useTrackSearch } from '@/features/solo/play/api/soloMatchApi'
import { useAuthStore } from '@/shared/api/authStore'
import { useRoomRealtime, useRoomStore } from '@/shared/realtime'
import { ERRORS_QUEUE, PRIVATE_QUEUE, topicDestination } from '@/shared/realtime/commands'
import type { RoomResponse, ServerRealtimeMessage } from '@/shared/realtime/messages'
import { parseServerMessage } from '@/shared/realtime/parse'
import { acquireRoomSocket } from '@/shared/realtime/roomConnection'
import { Button } from '@/shared/ui/Button'
import { Skeleton } from '@/shared/ui/Skeleton'
import { RoomProblem } from '../lobby/components/RoomProblem'
import { ConnectionBanner } from '../lobby/components/ConnectionBanner'
import { LobbyAlert } from '../lobby/components/LobbyAlert'
import { errorText } from '../lobby/model/errorText'
import { GuestLoginBanner } from './components/GuestLoginBanner'
import { GuessFeed } from './components/GuessFeed'
import { NoticeBar } from './components/NoticeBar'
import { Podium } from './components/Podium'
import { NextRoundCountdown, RoundResults } from './components/RoundResults'
import { Scoreboard } from './components/Scoreboard'
import { WaitPanel } from './components/WaitPanel'
import { finalTable, RANK_WAIT_MS, type RankView } from './model/finalTable'
import {
  INITIAL_MATCH,
  matchReducer,
  rosterOf,
  type MatchState,
  type Reveal,
} from './model/matchState'
import { parseRankUpdated } from './model/rankUpdated'
import { standingsOf } from './model/standings'

function initialState(room: RoomResponse): MatchState {
  return { ...INITIAL_MATCH, roster: rosterOf(room.players, {}) }
}

/** The answer of a finished round, in the shape the shared reveal draws, for this player. */
function resultFor(reveal: Reveal, me: string | null, state: MatchState): RoundResult {
  const mine = reveal.results.find((row) => row.userId === me)
  const { answer } = reveal
  return {
    roundNo: reveal.roundNo,
    outcome: mine?.outcome ?? 'MISSED',
    ...(mine?.correctLevel != null ? { correctLevel: levelIndex(mine.correctLevel) } : {}),
    baseScore: toBase(mine?.baseScore ?? 0),
    comboPercent: toCombo(mine?.comboPercent ?? 0),
    speedBonus: mine?.speedBonus ?? 0,
    roundScore: mine?.roundScore ?? 0,
    wrongCount: mine?.wrongCount ?? 0,
    comboStreak: me ? (state.scores[me]?.comboStreak ?? 0) : 0,
    totalScore: me ? (state.scores[me]?.totalScore ?? 0) : 0,
    answer: {
      trackId: answer.trackId,
      title: answer.trackTitle,
      artistName: answer.artistName,
      provider: 'DEEZER',
      listenUrl: answer.previewFullUrl,
      ...(answer.coverUrl ? { coverUrl: answer.coverUrl } : {}),
    },
  }
}

function toBase(value: number): RoundResult['baseScore'] {
  return value === 200 || value === 400 || value === 600 || value === 800 || value === 1000
    ? value
    : 0
}

function toCombo(value: number): RoundResult['comboPercent'] {
  return value === 10 || value === 20 ? value : 0
}

/** The server says the level in seconds (2, 5, 10, 20, 30); the shared round screen counts 1 to 5. */
function levelIndex(seconds: number): number {
  return Math.max(1, [2, 5, 10, 20, 30].indexOf(seconds) + 1)
}

/**
 * The match of one room, live. Every number on screen (scores, places, rank points, the answer) is
 * what the server sent; this component sends the player's commands and draws what comes back.
 */
export function LiveMatch({ initialRoom }: { initialRoom: RoomResponse }) {
  const { t } = useTranslation('room')
  const navigate = useNavigate()
  const me = useAuthStore((auth) => auth.user?.id ?? null)
  const roomId = initialRoom.roomId
  const [state, dispatch] = useReducer(matchReducer, initialRoom, initialState)
  useAchievementNotifications(state.phase === 'finished')
  // One player for the whole match: it decodes the clip while the round is prepared and the round
  // screen then plays that same buffer, so nothing is downloaded again once the clock runs.
  const [player] = useState(() => new SnippetPlayer())
  useEffect(
    () => () => {
      player.dispose()
    },
    [player],
  )
  const [query, setQuery] = useState('')
  const [pendingAt, setPendingAt] = useState<number | null>(null)
  const search = useTrackSearch(query)

  const receive = useCallback((message: ServerRealtimeMessage) => {
    dispatch({ type: 'message', message })
  }, [])
  const { link, send } = useRoomRealtime(roomId, {
    initialRoom,
    topics: ['round', 'scoreboard'],
    onMessage: receive,
  })

  // Clips, guess results and the snapshot come on the player's private queues, which the shared hook
  // does not offer to screens; the connection is the same one (it is shared per room).
  useEffect(() => {
    const { socket, release } = acquireRoomSocket(roomId)
    const feed = (body: string) => {
      const message = parseServerMessage(body)
      if (message) dispatch({ type: 'message', message })
    }
    const feedRank = (body: string) => {
      const message = parseRankUpdated(body)
      if (message) dispatch({ type: 'message', message })
    }
    const offs = [
      socket.subscribe(PRIVATE_QUEUE, feed),
      socket.subscribe(ERRORS_QUEUE, feed),
      socket.subscribe(topicDestination(roomId, 'scoreboard'), feedRank),
    ]
    return () => {
      for (const off of offs) off()
      release()
    }
  }, [roomId])

  // Arriving from the lobby, the first round's events went by before this screen existed: ask for the
  // whole picture once. (A fresh connection asks by itself.)
  const asked = useRef(false)
  useEffect(() => {
    if (asked.current || link !== 'live') return
    asked.current = true
    send('resync', {})
  }, [link, send])

  const stored = useRoomStore((s) => s.room)
  const storedPhase = useRoomStore((s) => s.phase)
  const lastError = useRoomStore((s) => s.lastError)
  const dismissError = useRoomStore((s) => s.dismissError)
  const kicked = useRoomStore((s) => me !== null && s.kickedUserIds.includes(me))
  const room = stored?.roomId === roomId ? stored : initialRoom

  useEffect(() => {
    dispatch({ type: 'roster', players: room.players })
  }, [room.players])

  // The room went back to its lobby (RETURN_TO_LOBBY, BR-12): everyone follows.
  const backToLobby = storedPhase === 'LOBBY' && link === 'live'
  useEffect(() => {
    if (backToLobby && room.code) {
      void navigate(`/r/${room.code}`, { replace: true, state: { room } })
    }
  }, [backToLobby, navigate, room])

  // Tell the server the round's clip is really in hand: fetched and decoded here first, so the round
  // clock does not start while this player is still downloading. A new clip, a new prepare or a fresh
  // snapshot (after a reconnect) asks for the confirmation again.
  const clip = state.clips[state.roundNo]
  const clipUrl = clip?.url
  useEffect(() => {
    if (state.phase !== 'prepare' || !clipUrl) return undefined
    let cancelled = false
    player
      .load(clipUrl)
      .then(() => {
        if (!cancelled) send('prepared', { roundNo: state.roundNo })
      })
      .catch(() => {
        // The server starts the round on its own deadline; the clip is retried when it plays.
      })
    return () => {
      cancelled = true
    }
  }, [state.phase, state.roundNo, state.syncs, clipUrl, send, player])

  // Quick match: the rank points come a moment after the final table; wait for them, but not forever.
  const quick = room.roomType === 'QUICK_MATCH'
  const finished = state.phase === 'finished'
  const [rankTimedOut, setRankTimedOut] = useState(false)
  useEffect(() => {
    if (!quick || !finished || state.rankChanges) return undefined
    const timer = setTimeout(() => {
      setRankTimedOut(true)
    }, RANK_WAIT_MS)
    return () => {
      clearTimeout(timer)
    }
  }, [quick, finished, state.rankChanges])
  const rankView: RankView = state.rankChanges ? 'ready' : rankTimedOut ? 'timeout' : 'waiting'

  const standings = standingsOf(state, room.players, me)
  const busy = pendingAt === state.replies
  const track = (action: () => boolean) => {
    if (action()) setPendingAt(state.replies)
  }

  if (kicked) return <RoomProblem kind="kicked" />

  const round: RoundState | null =
    clip && state.totalRounds
      ? {
          matchId: roomId,
          roundNo: state.roundNo,
          roundCount: state.totalRounds,
          level: levelIndex(clip.level),
          listenSeconds: clip.level,
          canListenMore: clip.level < 30,
          canSkip: clip.level === 30,
          audio: { url: clip.url, expiresAt: clip.expiresAt },
          serverTime: state.timer?.serverTime ?? '',
        }
      : null
  const myScore = me ? (state.scores[me]?.totalScore ?? 0) : 0

  const leave = () => {
    send('leave', {})
    useRoomStore.getState().reset()
    void navigate('/quick-match')
  }

  let main
  if (state.phase === 'finished') {
    main = (
      <div className="space-y-8">
        <Podium
          table={finalTable(state, standings)}
          standings={standings}
          rank={quick ? { view: rankView, changes: state.rankChanges } : undefined}
          me={me}
        />
        <div className="flex flex-wrap gap-3">
          {room.code ? (
            <Button
              onClick={() => {
                void navigate(`/r/${room.code ?? ''}`, { state: { room } })
              }}
            >
              {t('match.podium.playAgain')}
            </Button>
          ) : (
            <Button onClick={leave}>{t('match.podium.newMatch')}</Button>
          )}
          <Button variant="ghost" asChild>
            <Link to="/">{t('problem.home')}</Link>
          </Button>
        </div>
        <GuestLoginBanner />
      </div>
    )
  } else if (state.phase === 'reveal' && state.reveal) {
    main = (
      <div className="space-y-8">
        <RoundReveal result={resultFor(state.reveal, me, state)} matchFinished={false} />
        <NextRoundCountdown reveal={state.reveal} />
        <RoundResults reveal={state.reveal} standings={standings} />
      </div>
    )
  } else if (state.phase === 'playing' && (state.hasGuessed || state.out)) {
    main = <WaitPanel score={state.myScore} level={clip?.level ?? 2} />
  } else if (state.phase === 'playing' && round) {
    main = (
      <GameRound
        round={round}
        player={player}
        totalScore={myScore}
        combo={{ streak: me ? (state.scores[me]?.comboStreak ?? 0) : 0 }}
        timer={state.timer ?? undefined}
        suggestions={search.suggestions}
        suggestionsLoading={search.loading}
        verdict={state.verdict}
        busy={busy}
        onSearch={setQuery}
        onGuess={(trackId) => {
          track(() => send('guess', { roundNo: state.roundNo, trackId }))
        }}
        onListenMore={() => {
          const current = round.listenSeconds
          if (current === 30) return
          track(() => send('listen-more', { roundNo: state.roundNo, currentLevel: current }))
        }}
        onSkip={() => {
          if (send('give-up', { roundNo: state.roundNo })) dispatch({ type: 'gave-up' })
        }}
        onAudioExpired={() => {
          send('resync', {})
        }}
      />
    )
  } else {
    main = (
      <div role="status" className="space-y-3">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-64 w-full" />
        <span className="sr-only">
          {state.roundNo > 0 && state.totalRounds
            ? t('match.prepare', { n: state.roundNo, total: state.totalRounds })
            : t('match.joining')}
        </span>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-6 lg:py-10">
      <h1 className="sr-only">{t('match.title')}</h1>
      <div className="space-y-3 empty:hidden">
        <ConnectionBanner link={link} />
        <NoticeBar notices={state.notices} />
        {lastError ? <LobbyAlert text={errorText(lastError, t)} onDismiss={dismissError} /> : null}
      </div>
      <div className="mt-4 grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start lg:gap-10">
        <div className="space-y-4">
          {main}
          {state.phase === 'playing' ? <GuessFeed guessed={state.guessed} me={me} /> : null}
        </div>
        <aside aria-label={t('match.sidebar')} className="space-y-6 max-lg:order-first">
          <Scoreboard standings={standings} />
          <RoomChat roomId={roomId} />
        </aside>
      </div>
    </div>
  )
}
