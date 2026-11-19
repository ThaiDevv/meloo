import { AdminApiError, adminErrorKey } from '../shell/adminApi'

export function failureKey(error: unknown) {
  if (error instanceof AdminApiError) {
    if (error.code === 'ADMIN_SELF_ACTION_FORBIDDEN') return 'manage.selfAction'
    if (error.code === 'DAILY_CHALLENGE_LOCKED') return 'manage.dailyLocked'
    if (error.code === 'BANNED_WORD_ALREADY_EXISTS') return 'manage.wordExists'
  }
  return adminErrorKey(error)
}

export const UUID_PATTERN =
  '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'

export function formText(data: FormData, name: string) {
  const value = data.get(name)
  return typeof value === 'string' ? value.trim() : ''
}
