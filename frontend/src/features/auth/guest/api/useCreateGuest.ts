import { useMutation } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import { useAuthStore } from '@/shared/api/authStore'
import type { GuestFormValues } from '../nickname'
import { useAvatarStore } from '../store'

export type CreateGuestFailure = 'rejected' | 'rate-limited' | 'unknown'

export class CreateGuestError extends Error {
  readonly reason: CreateGuestFailure

  constructor(reason: CreateGuestFailure) {
    super(`createGuest failed: ${reason}`)
    this.reason = reason
  }
}

function failureOf(status: number): CreateGuestFailure {
  if (status === 400) return 'rejected'
  if (status === 429) return 'rate-limited'
  return 'unknown'
}

/** POST /auth/guest. On success the session is in memory and the guard lets the guest through. */
export function useCreateGuest() {
  return useMutation({
    mutationFn: async ({ nickname }: GuestFormValues) => {
      const { data, response } = await apiClient.POST('/api/v1/auth/guest', {
        body: { nickname },
      })
      if (!data) throw new CreateGuestError(failureOf(response.status))
      return data
    },
    onSuccess: (session, { avatarId }) => {
      useAvatarStore.getState().setAvatarId(avatarId)
      useAuthStore.getState().setSession(session)
    },
  })
}
