import type { ErrorMessage } from '@/shared/realtime/messages'

/** Which sentence to show for an error the server sent back for a lobby command. */
export function errorText(
  error: ErrorMessage,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  if (error.code === 'ROOM_CANNOT_START' && error.startFailureReason) {
    return t(`errors.start.${error.startFailureReason}`, { n: error.missingTracks ?? 0 })
  }
  switch (error.code) {
    case 'NOT_HOST':
    case 'NOT_ROOM_HOST':
      return t('errors.notHost')
    case 'ROOM_NOT_LOBBY':
    case 'INVALID_STATE':
      return t('errors.notInLobby')
    case 'NOT_ENOUGH_TRACKS':
      return t('errors.start.NOT_ENOUGH_TRACKS', { n: error.missingTracks ?? 0 })
    case 'RATE_LIMITED':
      return t('errors.rateLimited')
    case 'ALREADY_READY':
      return t('errors.alreadyReady')
    default:
      return t('errors.generic')
  }
}
