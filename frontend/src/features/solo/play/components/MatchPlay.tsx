import { X } from 'lucide-react'
import { useReducer, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { GameRound } from '@/features/game-core'
import { Button } from '@/shared/ui/Button'
import { toast } from '@/shared/ui/toast-store'
import {
  SoloApiError,
  useSoloMatchActions,
  useTrackSearch,
  type MatchResult,
  type SoloMatch,
} from '../api/soloMatchApi'
import { initialPlayState, playReducer } from '../model/matchState'

interface MatchPlayProps {
  match: SoloMatch
  onExit: () => void
  /** The server's result of the whole match, once the last round is over. */
  onFinished: (result: MatchResult) => void
}

/**
 * The rounds of one match: GameRound draws them, this component only carries the player's actions
 * to the server and the server's answers back. Scores, combos and answers are never computed here.
 */
export function MatchPlay({ match, onExit, onFinished }: MatchPlayProps) {
  const { t } = useTranslation('solo')
  const navigate = useNavigate()
  const [state, dispatch] = useReducer(playReducer, match, initialPlayState)
  const [query, setQuery] = useState('')
  const search = useTrackSearch(query)
  const actions = useSoloMatchActions(match.matchId)
  const nonce = useRef(0)
  const { round } = state

  const finish = () => {
    actions.result.mutate(undefined, {
      onSuccess: onFinished,
      onError: fail,
    })
  }

  /** The screen and the server disagree (a double click, another tab): take the server's round. */
  const resync = () => {
    actions.currentRound.mutate(undefined, {
      onSuccess: (fresh) => {
        dispatch({ type: 'resynced', round: fresh })
      },
      onError: (error) => {
        if (error instanceof SoloApiError && error.status === 409) finish()
        else fail(error)
      },
    })
  }

  function fail(error: unknown) {
    if (error instanceof SoloApiError) {
      if (error.status === 401 || error.status === 403) {
        void navigate('/start', { state: { from: `/solo/${match.matchId}` } })
        return
      }
      if (error.status === 409) {
        resync()
        return
      }
      if (error.status === 429) {
        toast({ title: t('play.errors.rateLimited'), tone: 'negative' })
        return
      }
    }
    toast({ title: t('play.errors.action'), tone: 'negative' })
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-1">
          <h1 className="text-base font-semibold">{t(`play.mode.${match.mode}`)}</h1>
          <p className="text-sm text-muted-foreground">
            {match.ranked ? t('play.music.standard') : t('play.status.practice')}
          </p>
        </div>
        <Button variant="ghost" size="sm" className="-mr-3.5" onClick={onExit}>
          <X aria-hidden="true" className="size-4" strokeWidth={2} />
          {t('play.exit.button')}
        </Button>
      </div>

      <GameRound
        round={round}
        totalScore={state.totalScore}
        combo={{ streak: state.comboStreak }}
        suggestions={search.suggestions}
        suggestionsLoading={search.loading}
        verdict={state.verdict}
        busy={actions.busy}
        result={state.result}
        matchFinished={state.matchFinished}
        preloadUrls={state.nextRound ? [state.nextRound.audio.url] : undefined}
        onSearch={setQuery}
        onGuess={(trackId) => {
          actions.guess.mutate(
            { roundNo: round.roundNo, trackId },
            {
              onSuccess: (response) => {
                nonce.current += 1
                dispatch({ type: 'guessed', response, nonce: nonce.current })
              },
              onError: fail,
            },
          )
        }}
        onListenMore={(expectedLevel) => {
          actions.listenMore.mutate(
            { roundNo: round.roundNo, expectedLevel },
            {
              onSuccess: (next) => {
                dispatch({ type: 'level-opened', round: next })
              },
              onError: fail,
            },
          )
        }}
        onSkip={() => {
          actions.skip.mutate(
            { roundNo: round.roundNo },
            {
              onSuccess: (end) => {
                dispatch({ type: 'round-ended', end })
              },
              onError: fail,
            },
          )
        }}
        onAudioExpired={resync}
        onContinue={() => {
          setQuery('')
          if (state.matchFinished) finish()
          else dispatch({ type: 'continued' })
        }}
      />
    </div>
  )
}
