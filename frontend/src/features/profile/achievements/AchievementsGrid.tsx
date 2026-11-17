import { Award, LockKeyhole } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/shared/api/authStore'
import { Badge } from '@/shared/ui/Badge'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { useAchievements } from './api/achievementsApi'
import { markSeen } from './seen'

export function AchievementsGrid() {
  const { t, i18n } = useTranslation('profile')
  const query = useAchievements()
  const userId = useAuthStore((s) => s.user?.id ?? null)
  useEffect(() => {
    if (userId && query.data) markSeen(userId, query.data.items)
  }, [userId, query.data])
  const vi = i18n.resolvedLanguage?.startsWith('vi') ?? true

  return (
    <section aria-labelledby="achievements-heading" className="space-y-4">
      <h2 id="achievements-heading" className="font-display text-2xl font-extrabold tracking-tight">
        {t('achievements.heading')}
      </h2>
      {query.isPending ? (
        <div aria-busy="true" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <span className="sr-only">{t('loading')}</span>
          {[0, 1, 2].map((key) => (
            <Skeleton key={key} className="h-40 rounded-2xl" />
          ))}
        </div>
      ) : query.isError ? (
        <ErrorState
          description={t('achievements.error')}
          onRetry={() => {
            void query.refetch()
          }}
        />
      ) : query.data.items.length === 0 ? (
        <EmptyState title={t('achievements.empty')} />
      ) : (
        <>
          <p className="num text-sm text-muted-foreground">
            {t('achievements.count', {
              unlocked: query.data.unlockedCount,
              total: query.data.totalCount,
            })}
          </p>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[...query.data.items]
              .sort((a, b) => a.sortOrder - b.sortOrder)
              .map((item) => {
                const Icon = item.unlocked ? Award : LockKeyhole
                return (
                  <li key={item.code} className="space-y-3 rounded-2xl bg-surface p-5">
                    <div className="flex items-center justify-between gap-3">
                      <Icon
                        aria-hidden="true"
                        className={`size-6 shrink-0 ${item.unlocked ? 'text-positive' : 'text-muted-foreground'}`}
                      />
                      <Badge tone={item.unlocked ? 'positive' : 'neutral'}>
                        {t(item.unlocked ? 'achievements.unlocked' : 'achievements.locked')}
                      </Badge>
                    </div>
                    <h3 className="font-display text-lg font-bold">
                      {vi ? item.nameVi : item.nameEn}
                    </h3>
                    <p className="text-sm text-muted-foreground">
                      {vi ? item.descriptionVi : item.descriptionEn}
                    </p>
                    {item.unlocked && item.unlockedAt ? (
                      <p className="text-xs text-muted-foreground">
                        {t('achievements.date', {
                          date: new Intl.DateTimeFormat(i18n.language, {
                            dateStyle: 'medium',
                          }).format(new Date(item.unlockedAt)),
                        })}
                      </p>
                    ) : null}
                  </li>
                )
              })}
          </ul>
        </>
      )}
    </section>
  )
}
