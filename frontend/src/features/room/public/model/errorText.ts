import type { QuickMatchFailure } from '../api/publicRoomsApi'

/** The sentence for a failed matchmaking or private-room request. */
export function failureText(
  failure: QuickMatchFailure,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  switch (failure.reason) {
    case 'not-enough-tracks':
      return t('quick.errors.notEnoughTracks', { missing: failure.missingTracks })
    case 'playlist-not-found':
      return t('create.errors.playlistNotFound')
    case 'rate-limited':
      return t('create.errors.rateLimited')
    case 'locked':
      return t('create.errors.locked')
    case 'rejected':
      return t('create.errors.rejected')
    default:
      return t('quick.errors.unknown')
  }
}
