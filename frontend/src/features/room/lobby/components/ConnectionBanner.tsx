import { WifiOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { RoomLink } from '@/shared/realtime'

/**
 * Says when what the player sees may be out of date: the socket is down (reconnecting) or a fresh
 * snapshot has not arrived yet. Nothing is shown while the picture is live.
 */
export function ConnectionBanner({ link }: { link: RoomLink }) {
  const { t } = useTranslation('room')
  if (link === 'live' || link === 'idle') return null
  return (
    <p
      role="status"
      className="flex items-center gap-3 rounded-xl bg-warning-soft px-4 py-3 text-sm text-warning"
    >
      <WifiOff aria-hidden="true" className="size-4 shrink-0" strokeWidth={1.75} />
      {t(link === 'reconnecting' ? 'link.reconnecting' : 'link.connecting')}
    </p>
  )
}
