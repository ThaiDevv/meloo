import { useTranslation } from 'react-i18next'
import type { Notice } from '../model/matchState'

/** Things that happened to the match itself: a track swapped (BR-27), the match cut short (BR-14). */
export function NoticeBar({ notices }: { notices: readonly Notice[] }) {
  const { t } = useTranslation('room')
  return (
    <div className="space-y-2 empty:hidden">
      {notices.map((notice) => (
        <p key={notice.id} role="status" className="rounded-xl bg-raised px-4 py-3 text-sm">
          {notice.kind === 'trackReplaced'
            ? t('match.notice.trackReplaced', { n: notice.roundNo })
            : t(`match.notice.earlyEnded.${notice.reason}`)}
        </p>
      ))}
    </div>
  )
}
