import { useTranslation } from 'react-i18next'
import { formatDayMonthYear } from '@/shared/daily'
import { Button } from '@/shared/ui/Button'
import { StreakStats } from './StreakStats'

interface DailyIntroProps {
  challengeDate: string
  currentStreak: number
  longestStreak: number
  starting: boolean
  onStart: () => void
}

/** Before the one attempt of the day: what it is, the streak so far, and the button that starts the clock. */
export function DailyIntro({
  challengeDate,
  currentStreak,
  longestStreak,
  starting,
  onStart,
}: DailyIntroProps) {
  const { t } = useTranslation('daily')
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <div className="space-y-3">
        <p className="num text-sm font-semibold text-muted-foreground">
          {formatDayMonthYear(challengeDate)}
        </p>
        <h1 className="font-display text-[clamp(2.25rem,6vw,4rem)] leading-none font-extrabold tracking-[-0.03em]">
          {t('title')}
        </h1>
        <p className="text-lg">{t('intro.body')}</p>
        <p className="text-sm text-muted-foreground">{t('intro.rules')}</p>
      </div>
      <StreakStats current={currentStreak} longest={longestStreak} />
      <div>
        <Button className="h-12 px-8" disabled={starting} onClick={onStart}>
          {t('intro.start')}
        </Button>
      </div>
    </div>
  )
}
