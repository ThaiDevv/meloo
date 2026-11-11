import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { Avatar } from '@/shared/ui/Avatar'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { useMyMatches, useMyProfile, useMyStats } from './api/profileApi'
import { MatchHistory, MatchHistorySkeleton } from './components/MatchHistory'
import { StatsPanel, StatsSkeleton } from './components/StatsPanel'
import { pageIndexFromParam } from './lib/format'

/** /profile: who I am, how I play, and every match I finished. */
export function ProfilePage() {
  const { t } = useTranslation('profile')
  return (
    <div className="mx-auto w-full max-w-5xl space-y-12 px-4 py-10 lg:py-16">
      <ProfileHeader />
      <section aria-labelledby="stats-heading" className="space-y-4">
        <h2 id="stats-heading" className="font-display text-2xl font-extrabold tracking-tight">
          {t('stats.heading')}
        </h2>
        <StatsSection />
      </section>
      <section aria-labelledby="history-heading" className="space-y-4">
        <h2 id="history-heading" className="font-display text-2xl font-extrabold tracking-tight">
          {t('history.heading')}
        </h2>
        <HistorySection />
      </section>
    </div>
  )
}

function ProfileHeader() {
  const { t, i18n } = useTranslation('profile')
  const profile = useMyProfile()
  const number = new Intl.NumberFormat(i18n.language)

  if (profile.isPending) {
    return (
      <header aria-busy="true" className="flex items-center gap-4">
        <h1 className="sr-only">{t('title')}</h1>
        <Skeleton className="size-14 rounded-full" />
        <div className="space-y-2">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-32" />
        </div>
      </header>
    )
  }
  if (profile.isError) {
    return (
      <header className="space-y-4">
        <h1 className="font-display text-4xl font-extrabold tracking-tight">{t('title')}</h1>
        <ErrorState
          description={t('errors.profile')}
          onRetry={() => {
            void profile.refetch()
          }}
        />
      </header>
    )
  }

  const me = profile.data
  return (
    <header className="flex flex-wrap items-center gap-4">
      <Avatar name={me.nickname} src={me.avatarUrl ?? undefined} size="lg" />
      <div className="min-w-0 space-y-1">
        <h1 className="flex flex-wrap items-center gap-3 font-display text-[clamp(2rem,6vw,3rem)] leading-none font-extrabold tracking-tight">
          <span className="break-all">{me.nickname}</span>
          {me.kind === 'GUEST' ? <Badge>{t('guest')}</Badge> : null}
        </h1>
        <p className="num text-muted-foreground">
          {t('header.points', {
            rank: number.format(me.rankPoints),
            xp: number.format(me.xp),
          })}
        </p>
      </div>
    </header>
  )
}

function StatsSection() {
  const { t } = useTranslation('profile')
  const stats = useMyStats()
  if (stats.isPending) {
    return (
      <div aria-busy="true">
        <span className="sr-only">{t('loading')}</span>
        <StatsSkeleton />
      </div>
    )
  }
  if (stats.isError) {
    return (
      <ErrorState
        description={t('errors.stats')}
        onRetry={() => {
          void stats.refetch()
        }}
      />
    )
  }
  if (stats.data.matchesPlayed === 0) {
    return <EmptyState title={t('stats.emptyTitle')} description={t('stats.emptyBody')} />
  }
  return <StatsPanel stats={stats.data} />
}

function HistorySection() {
  const { t } = useTranslation('profile')
  const [params, setParams] = useSearchParams()
  const page = pageIndexFromParam(params.get('page'))
  const history = useMyMatches(page)

  const goTo = (next: number) => {
    setParams(
      (current) => {
        const copy = new URLSearchParams(current)
        if (next <= 0) copy.delete('page')
        else copy.set('page', String(next + 1))
        return copy
      },
      { replace: false },
    )
  }

  if (history.isPending) {
    return (
      <div aria-busy="true">
        <span className="sr-only">{t('loading')}</span>
        <MatchHistorySkeleton />
      </div>
    )
  }
  if (history.isError) {
    return (
      <ErrorState
        description={t('errors.history')}
        onRetry={() => {
          void history.refetch()
        }}
      />
    )
  }
  if (history.data.items.length === 0) {
    if (history.data.totalItems > 0) {
      // A page past the end (an old link): offer the first page instead of an empty list.
      return (
        <EmptyState
          title={t('history.pastEnd')}
          action={
            <Button
              variant="outline"
              onClick={() => {
                goTo(0)
              }}
            >
              {t('history.firstPage')}
            </Button>
          }
        />
      )
    }
    return (
      <EmptyState
        title={t('history.emptyTitle')}
        description={t('history.emptyBody')}
        action={
          <Button asChild>
            <Link to="/solo">{t('history.playFirst')}</Link>
          </Button>
        }
      />
    )
  }
  return <MatchHistory history={history.data} fetching={history.isPlaceholderData} onPage={goTo} />
}
