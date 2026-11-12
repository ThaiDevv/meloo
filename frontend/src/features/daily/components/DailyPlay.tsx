import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { GameRound, type GuessVerdict } from '@/features/game-core'
import { DAILY_HISTORY_KEY } from '@/shared/daily'
import { toast } from '@/shared/ui/toast-store'
import {
  DAILY_TODAY_KEY,
  DailyApiError,
  fetchDailyToday,
  isSignedOut,
  useDailyActions,
  useTrackSearch,
  type DailyResult,
  type DailyRoundState,
  type DailyToday,
} from '../api/dailyApi'
import { toRoundState } from '../lib/mapping'

interface DailyPlayProps {
  initial: DailyRoundState
}

/**
 * The one attempt of the day. game-core draws the round; this component carries the player's actions
 * to the server and the server's answers back. Levels, scores, streaks and the answer are never
 * worked out here, and the 5-minute ceiling is the server's (`expiresAt`).
 */
export function DailyPlay({ initial }: DailyPlayProps) {
  const { t } = useTranslation('daily')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const actions = useDailyActions()
  const [round, setRound] = useState(initial)
  const [verdict, setVerdict] = useState<GuessVerdict | null>(null)
  const [query, setQuery] = useState('')
  const search = useTrackSearch(query)
  const nonce = useRef(0)

  /** The attempt is over: the page shows the result of the day. */
  const finish = (result: DailyResult) => {
    queryClient.setQueryData<DailyToday>(DAILY_TODAY_KEY, (today) =>
      today
        ? {
            ...today,
            status: result.status,
            currentStreak: result.currentStreak,
            longestStreak: result.longestStreak,
            round: undefined,
            result,
          }
        : today,
    )
    void queryClient.invalidateQueries({ queryKey: DAILY_HISTORY_KEY })
  }

  /** The screen and the server disagree (a double click, another tab, the ceiling passed): take the server's view. */
  const resync = () => {
    fetchDailyToday().then(
      (today) => {
        queryClient.setQueryData(DAILY_TODAY_KEY, today)
        if (today.status === 'IN_PROGRESS' && today.round) setRound(today.round)
      },
      (error: unknown) => {
        // Not through `fail`: a 409 there would only ask the server again.
        if (isSignedOut(error)) void navigate('/start', { state: { from: '/daily' } })
        else toast({ title: t('errors.action'), tone: 'negative' })
      },
    )
  }

  function fail(error: unknown) {
    if (error instanceof DailyApiError) {
      if (isSignedOut(error)) {
        void navigate('/start', { state: { from: '/daily' } })
        return
      }
      if (error.status === 409) {
        resync()
        return
      }
      if (error.status === 429) {
        toast({ title: t('errors.rateLimited'), tone: 'negative' })
        return
      }
    }
    toast({ title: t('errors.action'), tone: 'negative' })
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-base font-semibold">{t('title')}</h1>
      <GameRound
        round={toRoundState(round)}
        timer={{ endsAt: round.expiresAt, serverTime: round.serverTime }}
        suggestions={search.suggestions}
        suggestionsLoading={search.loading}
        verdict={verdict}
        busy={actions.busy}
        onSearch={setQuery}
        onGuess={(trackId) => {
          actions.guess.mutate(trackId, {
            onSuccess: (response) => {
              nonce.current += 1
              setVerdict({
                correct: response.correct,
                rightArtist: response.rightArtist,
                nonce: nonce.current,
              })
              if (response.attemptEnded && response.result) finish(response.result)
              else if (response.round) setRound(response.round)
            },
            onError: fail,
          })
        }}
        onListenMore={(expectedLevel) => {
          actions.listenMore.mutate(expectedLevel, {
            onSuccess: setRound,
            onError: fail,
          })
        }}
        onSkip={() => {
          actions.skip.mutate(undefined, { onSuccess: finish, onError: fail })
        }}
        onTimeout={resync}
        onAudioExpired={resync}
      />
    </div>
  )
}
