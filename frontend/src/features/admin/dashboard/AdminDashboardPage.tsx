import { lazy, Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import {
  ANALYTICS_RANGES,
  useAdminAnalytics,
  useAdminDashboard,
  type AdminAnalyticsDay,
  type AdminRatio,
  type AnalyticsRange,
} from './api/dashboardApi'
import { returningShare } from './lib'
import { KpiCard } from './components/KpiCard'

// Recharts is only needed inside admin; keep it out of the initial game bundle.
const DailyChart = lazy(async () => {
  const { DailyChart: Chart } = await import('./components/DailyChart')
  return { default: Chart }
})

function useFormat() {
  const { i18n } = useTranslation()
  const number = new Intl.NumberFormat(i18n.language)
  const percent = new Intl.NumberFormat(i18n.language, {
    style: 'percent',
    maximumFractionDigits: 1,
  })
  const day = new Intl.DateTimeFormat(i18n.language, {
    day: '2-digit',
    month: '2-digit',
    timeZone: 'UTC',
  })
  return {
    number: (value: number) => number.format(value),
    percent: (value: number) => percent.format(value),
    // Days are plain dates (Vietnam calendar); format them without shifting time zones.
    day: (value: string) => day.format(new Date(`${value}T00:00:00Z`)),
  }
}

export function AdminDashboardPage() {
  const { t } = useTranslation('admin')
  return (
    <div className="space-y-8">
      <p className="text-sm text-muted-foreground">{t('dashboard.subtitle')}</p>
      <Snapshot />
      <Analytics />
    </div>
  )
}

function Snapshot() {
  const { t } = useTranslation('admin')
  const format = useFormat()
  const query = useAdminDashboard()

  if (query.isPending) return <KpiSkeleton count={8} />
  if (query.isError) return <ErrorState onRetry={() => void query.refetch()} />
  const d = query.data
  const cards = [
    ['totalRegisteredUsers', d.totalRegisteredUsers],
    ['totalGuestsToday', d.totalGuestsToday],
    ['matchesToday', d.matchesToday],
    ['dailyPlayersToday', d.dailyPlayersToday],
    ['activeRooms', d.activeRooms],
    ['onlinePlayers', d.onlinePlayers],
    ['openChatReports', d.openChatReports],
    ['enabledTracks', d.enabledTracks],
  ] as const
  return (
    <section aria-labelledby="admin-kpi" className="space-y-3">
      <h2 id="admin-kpi" className="text-lg font-semibold">
        {t('dashboard.kpi.title')}
      </h2>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {cards.map(([key, value]) => (
          <KpiCard
            key={key}
            label={t(`dashboard.kpi.${key}`)}
            value={format.number(value)}
            hint={
              key === 'enabledTracks'
                ? t('dashboard.kpi.disabledHint', { count: d.disabledTracks })
                : undefined
            }
          />
        ))}
      </div>
    </section>
  )
}

function Analytics() {
  const { t } = useTranslation('admin')
  const format = useFormat()
  const [days, setDays] = useState<AnalyticsRange>(30)
  const query = useAdminAnalytics(days)

  const ratio = (value: AdminRatio | null) =>
    value?.rate == null ? t('dashboard.notAvailable') : format.percent(value.rate)
  const ratioHint = (value: AdminRatio | null) =>
    value
      ? t('dashboard.ratioHint', {
          numerator: format.number(value.numerator),
          denominator: format.number(value.denominator),
        })
      : t('dashboard.analytics.betaNotSet')

  return (
    <section aria-labelledby="admin-analytics" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="admin-analytics" className="text-lg font-semibold">
          {t('dashboard.analytics.title')}
        </h2>
        <div role="group" aria-label={t('dashboard.analytics.range')} className="flex gap-1">
          {ANALYTICS_RANGES.map((range) => (
            <Button
              key={range}
              size="sm"
              variant={range === days ? 'default' : 'outline'}
              aria-pressed={range === days}
              onClick={() => {
                setDays(range)
              }}
            >
              {t('dashboard.analytics.days', { count: range })}
            </Button>
          ))}
        </div>
      </div>

      {query.isPending ? (
        <KpiSkeleton count={4} />
      ) : query.isError ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <KpiCard label={t('dashboard.analytics.dau')} value={format.number(query.data.dau)} />
            <KpiCard label={t('dashboard.analytics.wau')} value={format.number(query.data.wau)} />
            <KpiCard
              label={t('dashboard.analytics.returningRate')}
              value={ratio(query.data.returningRate)}
              hint={ratioHint(query.data.returningRate)}
            />
            <KpiCard
              label={t('dashboard.analytics.guestConversion')}
              value={ratio(query.data.guestConversion)}
              hint={ratioHint(query.data.guestConversion)}
            />
          </div>
          <Suspense fallback={<KpiSkeleton count={3} />}>
            <Charts daily={query.data.daily} />
          </Suspense>
        </>
      )}
    </section>
  )
}

function Charts({ daily }: { daily: AdminAnalyticsDay[] }) {
  const { t } = useTranslation('admin')
  const format = useFormat()
  const total = (pick: (d: AdminAnalyticsDay) => number) => daily.reduce((s, d) => s + pick(d), 0)
  const range = {
    from: daily[0] ? format.day(daily[0].day) : '',
    to: daily.at(-1) ? format.day(daily.at(-1)?.day ?? '') : '',
  }
  const shares = daily.map((d) => ({ day: d.day, share: returningShare(d) }))
  const latestShare = shares.findLast((s) => s.share !== null)?.share ?? null

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <DailyChart
        title={t('dashboard.charts.players')}
        summary={t('dashboard.charts.playersSummary', {
          ...range,
          active: format.number(Math.max(0, ...daily.map((d) => d.activePlayers))),
        })}
        kind="line"
        data={daily}
        formatDay={format.day}
        formatValue={format.number}
        series={[
          { key: 'activePlayers', label: t('dashboard.charts.active'), color: 'var(--primary)' },
          {
            key: 'returningPlayers',
            label: t('dashboard.charts.returning'),
            color: 'var(--warning)',
          },
        ]}
      />
      <DailyChart
        title={t('dashboard.charts.matches')}
        summary={t('dashboard.charts.matchesSummary', {
          ...range,
          total: format.number(total((d) => d.matches)),
        })}
        kind="bar"
        data={daily}
        formatDay={format.day}
        formatValue={format.number}
        series={[{ key: 'matches', label: t('dashboard.charts.matches'), color: 'var(--primary)' }]}
      />
      <div className="min-w-0">
        <DailyChart
          title={t('dashboard.charts.returningShare')}
          summary={t('dashboard.charts.returningShareSummary', {
            ...range,
            latest:
              latestShare === null
                ? t('dashboard.notAvailable')
                : format.percent(latestShare / 100),
          })}
          kind="line"
          data={shares}
          max={100}
          formatDay={format.day}
          formatValue={(value) => format.percent(value / 100)}
          series={[
            { key: 'share', label: t('dashboard.charts.returningShare'), color: 'var(--warning)' },
          ]}
        />
      </div>
    </div>
  )
}

function KpiSkeleton({ count }: { count: number }) {
  const { t } = useTranslation('admin')
  return (
    <div role="status" aria-busy="true" className="grid grid-cols-2 gap-3 md:grid-cols-4">
      <span className="sr-only">{t('loading')}</span>
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className="h-24" />
      ))}
    </div>
  )
}
