import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useBlocker, useNavigate, useParams } from 'react-router-dom'
import { toast } from '@/shared/ui/toast-store'
import { CreateSoloMatchError, useCreateSoloMatch } from '../setup/api/useCreateSoloMatch'
import {
  SoloApiError,
  useSoloMatchActions,
  useSoloMatchLoad,
  type CreateSoloMatchRequest,
  type MatchResult,
  type SoloMatch,
} from './api/soloMatchApi'
import { ExitDialog } from './components/ExitDialog'
import { MatchIntro } from './components/MatchIntro'
import { MatchPlay } from './components/MatchPlay'
import {
  MatchLoadError,
  MatchMissing,
  MatchSkeleton,
  OtherMatchInProgress,
} from './components/MatchStates'
import { MatchSummary } from './components/MatchSummary'
import { shareResult, shareText } from './lib/share'

export function SoloPlayPage() {
  const { matchId = '' } = useParams<{ matchId: string }>()
  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-10 lg:py-16">
      {/* A new match id (Play again) starts from a clean screen. */}
      <SoloMatchScreen key={matchId} matchId={matchId} />
    </div>
  )
}

function SoloMatchScreen({ matchId }: { matchId: string }) {
  const load = useSoloMatchLoad(matchId)
  const [ended, setEnded] = useState<MatchResult | null>(null)

  if (load.isPending) return <MatchSkeleton />
  if (load.isError) {
    return (
      <MatchLoadError
        onRetry={() => {
          void load.refetch()
        }}
      />
    )
  }

  const found = load.data
  switch (found.kind) {
    case 'signed-out':
      return <Navigate to="/start" replace state={{ from: `/solo/${matchId}` }} />
    case 'missing':
      return <MatchMissing />
    case 'other':
      return <OtherMatchInProgress activeMatchId={found.activeMatchId} />
    case 'finished':
      return <SummaryScreen result={found.result} replay={replayOfResult(found.result)} />
    case 'active':
      return ended ? (
        <SummaryScreen result={ended} replay={replayOfMatch(found.match)} />
      ) : (
        <ActiveMatch match={found.match} onEnded={setEnded} />
      )
  }
}

/** The same match again. The filter or playlist is only known while the match is in progress. */
function replayOfMatch(match: SoloMatch): CreateSoloMatchRequest {
  return {
    mode: 'PRACTICE',
    roundCount: match.roundCount,
    ...(match.playlistId
      ? { playlistId: match.playlistId }
      : match.filter && { filter: match.filter }),
  }
}

function replayOfResult(result: MatchResult): CreateSoloMatchRequest | null {
  return { mode: 'PRACTICE', roundCount: result.roundCount }
}

function ActiveMatch({
  match,
  onEnded,
}: {
  match: SoloMatch
  onEnded: (result: MatchResult) => void
}) {
  const { t } = useTranslation('solo')
  const navigate = useNavigate()
  const { abandon, result } = useSoloMatchActions(match.matchId)
  const [playing, setPlaying] = useState(false)
  const [asking, setAsking] = useState(false)
  const leaving = useRef(false)
  // Any link in the app (logo, header) asks first, like the Quit button.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !leaving.current && currentLocation.pathname !== nextLocation.pathname,
  )
  const blocked = blocker.state === 'blocked'

  useEffect(() => {
    if (!playing) return
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => {
      window.removeEventListener('beforeunload', warn)
    }
  }, [playing])

  const leave = () => {
    leaving.current = true
    if (blocker.state === 'blocked') blocker.proceed()
    else void navigate('/')
  }

  const stay = () => {
    setAsking(false)
    if (blocker.state === 'blocked') blocker.reset()
  }

  const confirm = () => {
    abandon.mutate(undefined, {
      onSuccess: (result) => {
        if (blocker.state === 'blocked') leave()
        else onEnded(result)
      },
      onError: (error) => {
        const status = error instanceof SoloApiError ? error.status : 0
        if (status === 401 || status === 403) {
          leaving.current = true
          void navigate('/start', { state: { from: `/solo/${match.matchId}` } })
        } else if (status === 409) {
          // Already over (it expired after 30 minutes, BR-23): show what the server kept.
          result.mutate(undefined, {
            onSuccess: onEnded,
            onError: () => {
              toast({ title: t('play.exit.failed'), tone: 'negative' })
            },
          })
        } else if (status === 429) {
          toast({ title: t('play.errors.rateLimited'), tone: 'negative' })
        } else {
          toast({ title: t('play.exit.failed'), tone: 'negative' })
        }
      },
    })
  }

  const ask = () => {
    setAsking(true)
  }

  return (
    <>
      {playing ? (
        <MatchPlay match={match} onExit={ask} onFinished={onEnded} />
      ) : (
        <MatchIntro
          match={match}
          onExit={ask}
          onStart={() => {
            setPlaying(true)
          }}
        />
      )}
      <ExitDialog
        open={asking || blocked}
        busy={abandon.isPending}
        onStay={stay}
        onConfirm={confirm}
      />
    </>
  )
}

function SummaryScreen({
  result,
  replay,
}: {
  result: MatchResult
  replay: CreateSoloMatchRequest | null
}) {
  const { t, i18n } = useTranslation('solo')
  const navigate = useNavigate()
  const create = useCreateSoloMatch()

  const onReplay = () => {
    if (!replay) {
      void navigate('/solo')
      return
    }
    create.mutate(replay, {
      onSuccess: (next) => {
        void navigate(`/solo/${next.matchId}`)
      },
      onError: (error) => {
        const failure = error instanceof CreateSoloMatchError ? error.failure : null
        if (failure?.reason === 'in-progress') {
          void navigate(`/solo/${failure.matchId}`)
        } else if (failure?.reason === 'unauthorized') {
          void navigate('/start', { state: { from: '/solo' } })
        } else if (failure?.reason === 'not-enough-tracks') {
          toast({
            title: t('setup.errors.notEnoughTracks', {
              n: replay.roundCount ?? result.roundCount,
              missing: failure.missingTracks,
            }),
            tone: 'negative',
          })
        } else if (failure?.reason === 'rate-limited') {
          toast({ title: t('setup.errors.rateLimited'), tone: 'negative' })
        } else {
          toast({ title: t('setup.errors.unknown'), tone: 'negative' })
        }
      },
    })
  }

  const onShare = () => {
    void shareResult({
      title: t('play.share.title'),
      text: shareText(result, t, i18n.language),
      url: `${window.location.origin}/solo`,
    }).then((outcome) => {
      if (outcome === 'copied') toast({ title: t('play.share.copied'), tone: 'positive' })
      if (outcome === 'failed') toast({ title: t('play.share.failed'), tone: 'negative' })
    })
  }

  return (
    <MatchSummary
      result={result}
      replaying={create.isPending}
      onReplay={onReplay}
      onShare={onShare}
    />
  )
}
