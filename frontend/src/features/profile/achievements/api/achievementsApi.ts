import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/shared/api/authStore'
import { apiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/schema.gen'

export type Achievement = components['schemas']['AchievementItem']
export const achievementsKey = (userId: string | null) => ['achievements', userId] as const

export async function fetchAchievements(signal?: AbortSignal) {
  const { data, response } = await apiClient.GET('/api/v1/me/achievements', { signal })
  if (!data) throw new Error(`listMyAchievements failed: ${String(response.status)}`)
  return data
}

export function useAchievements() {
  const userId = useAuthStore((s) => s.user?.id ?? null)
  return useQuery({
    queryKey: achievementsKey(userId),
    queryFn: ({ signal }) => fetchAchievements(signal),
  })
}
