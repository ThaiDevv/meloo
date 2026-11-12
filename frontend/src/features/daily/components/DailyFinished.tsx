import { useQueryClient } from '@tanstack/react-query'
import { Share2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { RoundReveal } from '@/features/game-core'
import { formatDayMonthYear } from '@/shared/daily'
import { Button } from '@/shared/ui/Button'
import { toast } from '@/shared/ui/toast-store'
import { DAILY_TODAY_KEY, useDailyResult, type DailyResult } from '../api/dailyApi'
import { dailyShareText, shareDaily } from '../lib/share'
import { toRoundResult } from '../lib/mapping'
import { Countdown } from './Countdown'
import { StreakStats } from './StreakStats'

/**
 * The attempt is over: the answer, the streak, the countdown to the next song and the share button.
 * The share text is built from `GET /daily/result`, the spoiler-free summary (BR-25).
 */
export function DailyFinished({ result }: { result: DailyResult }) {
  const { t } = useTranslation('daily')
  const queryClient = useQueryClient()
  const shared = useDailyResult(result.challengeDate)

  const onShare = () => {
    if (!shared.data) return
    void shareDaily({
      title: t('share.title'),
      text: dailyShareText(shared.data, t, `${window.location.origin}/daily`),
    }).then((outcome) => {
      if (outcome === 'copied') toast({ title: t('share.copied'), tone: 'positive' })
      if (outcome === 'failed') toast({ title: t('share.failed'), tone: 'negative' })
    })
  }

  return (
    <div className="flex flex-col gap-10">
      <header className="space-y-2">
        <p className="num text-sm font-semibold text-muted-foreground">
          {formatDayMonthYear(result.challengeDate)}
        </p>
        <h1 className="font-display text-3xl font-extrabold tracking-[-0.02em] sm:text-4xl">
          {t('finished.heading')}
        </h1>
      </header>

      <RoundReveal result={toRoundResult(result)} />

      <section
        aria-label={t('finished.progress')}
        className="grid gap-8 rounded-3xl bg-surface p-6 sm:p-8 md:grid-cols-2"
      >
        <div className="flex flex-col gap-6">
          <StreakStats current={result.currentStreak} longest={result.longestStreak} />
          {result.rank != null ? (
            <p className="text-sm text-muted-foreground">
              {t('finished.rank', { rank: result.rank })}
            </p>
          ) : null}
        </div>
        <div className="flex flex-col items-start gap-6">
          <Countdown
            nextResetAt={result.nextResetAt}
            onReached={() => {
              void queryClient.invalidateQueries({ queryKey: DAILY_TODAY_KEY })
            }}
          />
          <Button onClick={onShare} disabled={!shared.data}>
            <Share2 aria-hidden="true" className="size-4" strokeWidth={2} />
            {t('share.button')}
          </Button>
          {shared.isError ? (
            <p role="alert" className="text-sm text-negative">
              {t('share.loadFailed')}
            </p>
          ) : null}
        </div>
      </section>
    </div>
  )
}
