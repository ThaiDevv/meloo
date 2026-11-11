import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { Badge } from '@/shared/ui/Badge'
import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { useMyMatchDetail, useMyProfile, type MatchHistoryDetail } from './api/profileApi'
import { RankDelta } from './components/MatchHistory'
import { RoundList } from './components/RoundList'
import { Standings } from './components/Standings'

/** /profile/matches/:matchId: one finished match, round by round. */
export function MatchDetailPage() {
  const { t } = useTranslation('profile')
  const { matchId = '' } = useParams()
  const detail = useMyMatchDetail(matchId)

  return (
    <div className="mx-auto w-full max-w-4xl space-y-10 px-4 py-10 lg:py-16">
      <Link
        to="/profile"
        className="inline-flex items-center gap-2 text-sm font-semibold underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        {t('detail.back')}
      </Link>
      {detail.isPending ? (
        <DetailSkeleton />
      ) : detail.isError ? (
        <div className="space-y-4">
          <h1 className="font-display text-3xl font-extrabold tracking-tight">
            {t('detail.title')}
          </h1>
          <ErrorState
            description={t('errors.detail')}
            onRetry={() => {
              void detail.refetch()
            }}
          />
        </div>
      ) : detail.data === null ? (
        <div className="space-y-4">
          <h1 className="font-display text-3xl font-extrabold tracking-tight">
            {t('detail.notFoundTitle')}
          </h1>
          <EmptyState
            title={t('detail.notFoundBody')}
            action={
              <Button asChild variant="outline">
                <Link to="/profile">{t('detail.back')}</Link>
              </Button>
            }
          />
        </div>
      ) : (
        <Detail match={detail.data} />
      )}
    </div>
  )
}

function Detail({ match }: { match: MatchHistoryDetail }) {
  const { t, i18n } = useTranslation('profile')
  const profile = useMyProfile()
  const number = new Intl.NumberFormat(i18n.language)
  const date = new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(new Date(match.endedAt))
  const multiplayer = match.type !== 'SOLO' && match.players.length > 1

  return (
    <>
      <header className="space-y-3">
        <p className="flex flex-wrap items-center gap-2 text-muted-foreground">
          <time dateTime={match.endedAt}>{date}</time>
          {match.status === 'ABANDONED' ? (
            <Badge tone="warning">{t('history.abandoned')}</Badge>
          ) : null}
          {match.ranked ? <Badge>{t('history.ranked')}</Badge> : null}
        </p>
        <h1 className="font-display text-[clamp(2rem,6vw,3rem)] leading-none font-extrabold tracking-tight">
          {t(`matchType.${match.type}`)}
        </h1>
        <p className="num flex flex-wrap items-baseline gap-x-3">
          <span className="font-display text-5xl font-extrabold tracking-tight text-primary">
            {number.format(match.totalScore)}
          </span>
          <span className="text-lg font-semibold">{t('unit')}</span>
        </p>
        <p>
          {t('history.correct', { correct: match.correctCount, total: match.roundCount })}
          {match.type === 'SOLO'
            ? null
            : ` · ${t('history.placement', { rank: match.rank, players: match.playerCount })}`}
        </p>
        {match.rankDelta !== null && match.rankDelta !== undefined ? (
          <RankDelta delta={match.rankDelta} />
        ) : null}
      </header>

      {multiplayer ? (
        <section aria-labelledby="standings-heading" className="space-y-4">
          <h2
            id="standings-heading"
            className="font-display text-2xl font-extrabold tracking-tight"
          >
            {t('detail.standings')}
          </h2>
          <Standings players={match.players} meId={profile.data?.id} />
        </section>
      ) : null}

      <section aria-labelledby="rounds-heading" className="space-y-4">
        <h2 id="rounds-heading" className="font-display text-2xl font-extrabold tracking-tight">
          {t('detail.rounds')}
        </h2>
        {match.rounds.length === 0 ? (
          <EmptyState title={t('detail.noRounds')} />
        ) : (
          <RoundList rounds={match.rounds} />
        )}
      </section>
    </>
  )
}

function DetailSkeleton() {
  const { t } = useTranslation('profile')
  return (
    <div aria-busy="true" className="space-y-6">
      <span className="sr-only">{t('loading')}</span>
      <Skeleton className="h-4 w-40" />
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-12 w-40" />
      <div className="space-y-2">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-20 w-full rounded-2xl" />
        ))}
      </div>
    </div>
  )
}
