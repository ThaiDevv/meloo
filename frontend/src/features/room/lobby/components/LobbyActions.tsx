import { Check, LogOut, Play } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { RoomPlayerSummary } from '@/shared/realtime/messages'
import { Button } from '@/shared/ui/Button'

interface LobbyActionsProps {
  role: 'host' | 'member' | 'automatic'
  /** The player's own row; null while they only wait for the next match (BR-15). */
  self: RoomPlayerSummary | null
  canStart: boolean
  /** Names that still hold the start up, for the hint. */
  waitingFor: string[]
  playerCount: number
  /** False while the socket is down or a countdown already runs: commands would be lost or refused. */
  enabled: boolean
  onReady: (ready: boolean) => void
  onStart: () => void
  onLeave: () => void
}

function startHint(
  playerCount: number,
  waitingFor: string[],
  t: (key: string, options?: Record<string, unknown>) => string,
): string | null {
  if (playerCount < 2) return t('actions.needPlayers')
  if (waitingFor.length > 0) return t('actions.waitingFor', { names: waitingFor.join(', ') })
  return null
}

export function LobbyActions({
  role,
  self,
  canStart,
  waitingFor,
  playerCount,
  enabled,
  onReady,
  onStart,
  onLeave,
}: LobbyActionsProps) {
  const { t } = useTranslation('room')
  const hint = role === 'host' ? startHint(playerCount, waitingFor, t) : null
  return (
    <section aria-label={t('actions.label')} className="space-y-3">
      {role === 'host' ? (
        <>
          <Button
            className="h-14 w-full text-base"
            disabled={!enabled || !canStart}
            onClick={onStart}
          >
            <Play aria-hidden="true" className="size-4 fill-current" />
            {t('actions.start')}
          </Button>
          {hint ? <p className="text-center text-sm text-muted-foreground">{hint}</p> : null}
        </>
      ) : null}
      {role === 'member' && self ? (
        <Button
          className="h-14 w-full text-base"
          variant={self.isReady ? 'outline' : 'default'}
          disabled={!enabled}
          aria-pressed={self.isReady}
          onClick={() => {
            onReady(!self.isReady)
          }}
        >
          {self.isReady ? <Check aria-hidden="true" className="size-4" /> : null}
          {self.isReady ? t('actions.unready') : t('actions.ready')}
        </Button>
      ) : null}
      <Button className="w-full" variant="ghost" onClick={onLeave}>
        <LogOut aria-hidden="true" className="size-4" strokeWidth={1.75} />
        {t('actions.leave')}
      </Button>
    </section>
  )
}
