import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSessionStore } from '@/shared/session/store'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/Tabs'
import {
  useAllTimeLeaderboard,
  useDailyLeaderboard,
  type DailyLeaderboardEntry,
  type RankLeaderboardEntry,
} from './api/leaderboardApi'
import { GuestInvite } from './components/GuestInvite'
import { LeaderboardBoard } from './components/LeaderboardBoard'
import type { RowData } from './components/LeaderboardRow'

type Tab = 'all-time' | 'today'

export function LeaderboardPage() {
  const { t } = useTranslation('leaderboard')
  const role = useSessionStore((s) => s.role)
  const [tab, setTab] = useState<Tab>('all-time')

  return (
    <div className="mx-auto w-full max-w-2xl space-y-6 px-4 py-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('subtitle')}</p>
      </header>

      {role === 'USER' || role === 'ADMIN' ? null : <GuestInvite />}

      {/* The boards need a session (guest or registered); the header restores it after a reload. */}
      {role === null ? null : (
        <Tabs
          value={tab}
          onValueChange={(next) => {
            setTab(next === 'today' ? 'today' : 'all-time')
          }}
        >
          <TabsList aria-label={t('tabs.label')}>
            <TabsTrigger value="all-time">{t('tabs.allTime')}</TabsTrigger>
            <TabsTrigger value="today">{t('tabs.today')}</TabsTrigger>
          </TabsList>
          <TabsContent value="all-time">
            <AllTimeBoard />
          </TabsContent>
          <TabsContent value="today">
            <DailyBoard />
          </TabsContent>
        </Tabs>
      )}
    </div>
  )
}

function useNumber() {
  const { i18n } = useTranslation()
  return (value: number) => new Intl.NumberFormat(i18n.language).format(value)
}

function AllTimeBoard() {
  const { t } = useTranslation('leaderboard')
  const format = useNumber()
  const [page, setPage] = useState(0)
  const query = useAllTimeLeaderboard(page, true)

  const toRow = (entry: RankLeaderboardEntry): RowData => ({
    userId: entry.userId,
    rank: entry.rank,
    nickname: entry.nickname,
    avatarUrl: entry.avatarUrl,
    avatarId: entry.avatarId,
    value: t('allTime.points', { points: format(entry.rankPoints) }),
    detail: t('allTime.matches', {
      played: format(entry.matchesPlayed),
      won: format(entry.matchesWon),
    }),
  })

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">{t('allTime.description')}</p>
      <LeaderboardBoard
        label={t('tabs.allTime')}
        rows={query.data?.items.map(toRow)}
        myRow={query.data?.myEntry ? toRow(query.data.myEntry) : null}
        page={page}
        totalPages={query.data?.totalPages ?? 0}
        isPending={query.isPending}
        isError={query.isError}
        isFetching={query.isFetching}
        emptyTitle={t('allTime.emptyTitle')}
        emptyDescription={t('allTime.emptyDescription')}
        onRetry={() => void query.refetch()}
        onPageChange={setPage}
      />
    </div>
  )
}

function DailyBoard() {
  const { t, i18n } = useTranslation('leaderboard')
  const format = useNumber()
  const [page, setPage] = useState(0)
  const query = useDailyLeaderboard(page, true)

  const seconds = (ms: number) =>
    new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }).format(ms / 1000)

  const toRow = (entry: DailyLeaderboardEntry): RowData => ({
    userId: entry.userId,
    rank: entry.rank,
    nickname: entry.nickname,
    avatarUrl: entry.avatarUrl,
    avatarId: entry.avatarId,
    value: t('daily.score', { score: format(entry.score) }),
    detail:
      entry.status === 'SOLVED' && entry.listenSeconds != null
        ? t('daily.solved', { listen: entry.listenSeconds, time: seconds(entry.elapsedMs) })
        : t('daily.unsolved', { time: seconds(entry.elapsedMs) }),
  })

  const date = query.data?.challengeDate
  const dateLabel = date
    ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'long', timeZone: 'UTC' }).format(
        new Date(`${date}T00:00:00Z`),
      )
    : null

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        {dateLabel ? t('daily.descriptionWithDate', { date: dateLabel }) : t('daily.description')}
      </p>
      <LeaderboardBoard
        label={t('tabs.today')}
        rows={query.data?.items.map(toRow)}
        myRow={query.data?.myEntry ? toRow(query.data.myEntry) : null}
        page={page}
        totalPages={query.data?.totalPages ?? 0}
        isPending={query.isPending}
        isError={query.isError}
        isFetching={query.isFetching}
        emptyTitle={t('daily.emptyTitle')}
        emptyDescription={t('daily.emptyDescription')}
        onRetry={() => void query.refetch()}
        onPageChange={setPage}
      />
    </div>
  )
}
