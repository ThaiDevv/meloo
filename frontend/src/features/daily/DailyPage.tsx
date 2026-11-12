import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate } from 'react-router-dom'
import { useDailyScope } from '@/shared/daily'
import { toast } from '@/shared/ui/toast-store'
import {
  dailyTodayKey,
  DailyApiError,
  fetchDailyToday,
  isSignedOut,
  useDailyActions,
  useDailyToday,
} from './api/dailyApi'
import { DailyFinished } from './components/DailyFinished'
import { DailyIntro } from './components/DailyIntro'
import { DailyPlay } from './components/DailyPlay'
import { DailyLoadError, DailyNotAvailable, DailySkeleton } from './components/DailyStates'

export function DailyPage() {
  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-10 lg:py-16">
      <DailyScreen />
    </div>
  )
}

function DailyScreen() {
  const { t } = useTranslation('daily')
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const scope = useDailyScope()
  const today = useDailyToday()
  const { start } = useDailyActions()

  if (today.isPending) return <DailySkeleton />
  if (today.isError) {
    if (isSignedOut(today.error)) return <Navigate to="/start" replace state={{ from: '/daily' }} />
    if (today.error instanceof DailyApiError && today.error.status === 404) {
      return <DailyNotAvailable />
    }
    return (
      <DailyLoadError
        onRetry={() => {
          void today.refetch()
        }}
      />
    )
  }

  const data = today.data
  if (data.status === 'IN_PROGRESS' && data.round) {
    // A new `startedAt` is a new attempt: it starts from a clean screen.
    return <DailyPlay key={data.round.startedAt} initial={data.round} />
  }
  if ((data.status === 'SOLVED' || data.status === 'UNSOLVED') && data.result) {
    // The day rolled over while the page was open: the server says so when the countdown ends.
    return <DailyFinished key={data.result.challengeDate} result={data.result} />
  }

  const onStart = () => {
    start.mutate(undefined, {
      onSuccess: (round) => {
        queryClient.setQueryData(dailyTodayKey(scope), {
          ...data,
          challengeDate: round.challengeDate,
          status: 'IN_PROGRESS',
          round,
        })
      },
      onError: (error) => {
        const status = error instanceof DailyApiError ? error.status : 0
        if (isSignedOut(error)) {
          void navigate('/start', { state: { from: '/daily' } })
          return
        }
        if (status === 409) {
          // Already played today (another tab): show what the server has.
          fetchDailyToday().then(
            (fresh) => {
              queryClient.setQueryData(dailyTodayKey(scope), fresh)
            },
            () => {
              toast({ title: t('errors.action'), tone: 'negative' })
            },
          )
          return
        }
        toast({
          title: t(status === 429 ? 'errors.rateLimited' : 'errors.action'),
          tone: 'negative',
        })
      },
    })
  }

  return (
    <DailyIntro
      challengeDate={data.challengeDate}
      currentStreak={data.currentStreak}
      longestStreak={data.longestStreak}
      starting={start.isPending}
      onStart={onStart}
    />
  )
}
