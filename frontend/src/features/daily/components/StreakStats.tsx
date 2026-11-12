import { Flame } from 'lucide-react'
import { useTranslation } from 'react-i18next'

/** The streak the server counted (BR-25): consecutive days guessed. Shown as given, never worked out here. */
export function StreakStats({ current, longest }: { current: number; longest: number }) {
  const { t } = useTranslation('daily')
  return (
    <dl className="flex flex-wrap items-baseline gap-x-8 gap-y-2" data-testid="daily-streak">
      <div>
        <dt className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <Flame aria-hidden="true" className="size-4 text-primary" strokeWidth={2} />
          {t('streak.current')}
        </dt>
        <dd className="num font-display text-4xl font-extrabold tracking-tight">
          {t('streak.days', { count: current })}
        </dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">{t('streak.longest')}</dt>
        <dd className="num text-xl font-semibold">{t('streak.days', { count: longest })}</dd>
      </div>
    </dl>
  )
}
