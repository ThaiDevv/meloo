import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/shared/api/authStore'
import { toast } from '@/shared/ui/toast-store'
import { achievementsKey, fetchAchievements } from './api/achievementsApi'
import { readSeen, writeSeen } from './seen'

export const ACHIEVEMENTS_RECHECK_MS = 3000

/** Capture a quiet baseline during play, then check on the summary and once after async evaluation. */
export function useAchievementNotifications(finished: boolean | null) {
  const userId = useAuthStore((s) => s.user?.id ?? null)
  const client = useQueryClient()
  const { t, i18n } = useTranslation('profile')

  useEffect(() => {
    if (!userId || finished === null) return
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    const check = async (retry: boolean) => {
      let announced = false
      try {
        const data = await client.query({
          queryKey: achievementsKey(userId),
          queryFn: ({ signal }) => fetchAchievements(signal),
          staleTime: 0,
          retry: false,
        })
        if (!active || useAuthStore.getState().user?.id !== userId) return
        const previous = readSeen(userId)
        const next = previous ?? new Map<string, number>()
        for (const item of data.items) {
          if (!item.unlocked || !item.unlockedAt) continue
          const stamp = Date.parse(item.unlockedAt)
          if (!Number.isFinite(stamp)) continue
          if (finished && previous && stamp > (previous.get(item.code) ?? -Infinity)) {
            toast({
              title: t('achievements.toast'),
              description: i18n.resolvedLanguage?.startsWith('vi') ? item.nameVi : item.nameEn,
              tone: 'positive',
            })
            announced = true
          }
          next.set(item.code, Math.max(stamp, next.get(item.code) ?? -Infinity))
        }
        writeSeen(userId, next)
      } catch {
        // An optional celebration must not interrupt the result or trigger unbounded polling.
      }
      if (active && retry && !announced) {
        timer = setTimeout(() => {
          void check(false)
        }, ACHIEVEMENTS_RECHECK_MS)
      }
    }
    void check(finished)
    return () => {
      active = false
      clearTimeout(timer)
    }
  }, [client, userId, finished, t, i18n])
}
