import { useTranslation } from 'react-i18next'
import { formatDayMonth, localDay, useDailyHistory, type DailyHistory } from '@/shared/daily'
import { brightnessOf } from '@/shared/daily'
import { useSessionStore } from '@/shared/session/store'
import { Badge } from '@/shared/ui/Badge'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { Reveal } from './Reveal'
import { StreakGrid, type StreakLevel } from './StreakGrid'

/**
 * A made-up history for the picture only, the same on every visit: shown while there is no session,
 * so there is no real player's history to draw.
 */
function sampleLevelOf(today: string) {
  return (day: string): StreakLevel => {
    if (day === today) return 0
    const days = Math.floor(Date.parse(day) / 86_400_000)
    const roll = ((days * 2_654_435_761) >>> 0) % 100
    if (roll < 58) return 0
    if (roll < 70) return 1
    if (roll < 82) return 2
    if (roll < 91) return 3
    if (roll < 97) return 4
    return 5
  }
}

function Legend() {
  const { t } = useTranslation('home')
  return (
    <div
      aria-hidden="true"
      className="mt-5 flex flex-wrap items-center justify-end gap-x-3 gap-y-2 text-sm text-muted-foreground"
    >
      <span data-level={0} className="mh-cell size-3.5 rounded-[3px]" />
      <span>{t('streak.none')}</span>
      <span className="ml-2">{t('streak.late')}</span>
      {[0, 1, 2, 3, 4, 5].map((level) => (
        <span key={level} data-level={level} className="mh-cell size-3.5 rounded-[3px]" />
      ))}
      <span>{t('streak.early')}</span>
    </div>
  )
}

function RealCalendar({ history }: { history: DailyHistory }) {
  const { t, i18n } = useTranslation('home')
  const language = i18n.resolvedLanguage ?? 'vi'
  // A day that is not in `items` is dark: not played, or played and "not guessed".
  const seconds = new Map(history.items.map((item) => [item.date, item.level]))
  return (
    <>
      <StreakGrid
        today={history.today}
        levelOf={(day) => {
          const level = seconds.get(day)
          return level === undefined ? 0 : brightnessOf(level)
        }}
        titleOf={(day) =>
          t('streak.tooltip', {
            date: formatDayMonth(day, language),
            label: `${String(seconds.get(day))}s`,
          })
        }
      />
      <Legend />
      <p className="mt-4 text-sm text-muted-foreground" data-testid="streak-summary">
        {t('streak.summary', {
          count: history.items.length,
          current: history.currentStreak,
          longest: history.longestStreak,
        })}
      </p>
    </>
  )
}

/**
 * "Light up every day": the calendar of the daily challenge. Real days when the player has a session,
 * the labelled picture otherwise. A failed load stays inside this block and never blocks the page.
 */
export function StreakSection() {
  const { t } = useTranslation('home')
  const hasSession = useSessionStore((state) => state.role !== null)
  const history = useDailyHistory(hasSession)

  let body
  if (!hasSession) {
    body = (
      <>
        <StreakGrid levelOf={sampleLevelOf(localDay())} />
        <Legend />
      </>
    )
  } else if (history.isError) {
    body = (
      <ErrorState
        title={t('streak.error.title')}
        description={t('streak.error.body')}
        onRetry={() => {
          void history.refetch()
        }}
      />
    )
  } else if (history.data) {
    body = <RealCalendar history={history.data} />
  } else {
    body = (
      <div aria-busy="true">
        <p role="status" className="sr-only">
          {t('streak.loading')}
        </p>
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    )
  }

  return (
    <section aria-labelledby="streak-title" className="pb-20 lg:pb-32">
      <Reveal className="flex flex-wrap items-center gap-3">
        <h2
          id="streak-title"
          className="font-display text-3xl font-extrabold tracking-[-0.02em] sm:text-4xl"
        >
          {t('streak.heading')}
        </h2>
        {hasSession ? null : <Badge>{t('streak.badge')}</Badge>}
      </Reveal>
      <Reveal delay={80}>
        <p className="mt-3 max-w-xl text-lg">{t('streak.body')}</p>
      </Reveal>
      <Reveal delay={160} className="mt-8 rounded-2xl bg-surface p-5 sm:p-6">
        {body}
      </Reveal>
    </section>
  )
}
