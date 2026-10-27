import { z } from 'zod'
import { AVATAR_IDS } from './avatars'

/** BR-22: 3-20 characters. Banned words are checked by the server only (it owns the list). */
export const NICKNAME_MIN = 3
export const NICKNAME_MAX = 20

// Messages are i18n keys in the `auth` namespace; the form translates them.
export const guestFormSchema = z.object({
  nickname: z
    .string()
    .trim()
    .min(1, 'guest.error.required')
    .min(NICKNAME_MIN, 'guest.error.tooShort')
    .max(NICKNAME_MAX, 'guest.error.tooLong'),
  avatarId: z.enum(AVATAR_IDS),
})

export type GuestFormValues = z.infer<typeof guestFormSchema>
