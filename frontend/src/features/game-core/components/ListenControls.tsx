import { FastForward, Play, SkipForward } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { levelLabel, pointsForLevel } from '../lib/levels'

interface ListenControlsProps {
  /** Opened level, 1 to 5. */
  level: number
  /** Length of the next level, shown on the button that opens it. */
  nextListenSeconds?: number
  canListenMore: boolean
  canSkip: boolean
  /** A request is in flight: no second click may open two levels (the player would lose 200 points twice). */
  busy: boolean
  /** Playing is possible once the clip has loaded. */
  canReplay: boolean
  onReplay: () => void
  onListenMore: () => void
  onSkip: () => void
}

const NEXT_SECONDS = [5, 10, 20, 30]

/** Listen again (free, BR-03), listen more (BR-04) or, at 30 seconds, give up. All plain buttons: Tab and Enter work. */
export function ListenControls({
  level,
  nextListenSeconds,
  canListenMore,
  canSkip,
  busy,
  canReplay,
  onReplay,
  onListenMore,
  onSkip,
}: ListenControlsProps) {
  const { t } = useTranslation('game')
  const nextLabel = levelLabel(nextListenSeconds ?? NEXT_SECONDS[level - 1] ?? 30)

  return (
    <div className="grid grid-cols-2 gap-3">
      <Button
        variant="outline"
        onClick={onReplay}
        disabled={!canReplay}
        className="h-auto min-h-11 flex-col gap-0.5 py-2"
      >
        <span className="inline-flex items-center gap-2">
          <Play aria-hidden="true" className="size-4" strokeWidth={2} />
          {t('controls.replay')}
        </span>
        <span className="text-xs font-normal text-muted-foreground">
          {t('controls.replayHint')}
        </span>
      </Button>

      {canSkip ? (
        <Button
          variant="destructive"
          onClick={onSkip}
          disabled={busy}
          className="h-auto min-h-11 flex-col gap-0.5 py-2"
        >
          <span className="inline-flex items-center gap-2">
            <SkipForward aria-hidden="true" className="size-4" strokeWidth={2} />
            {t('controls.skip')}
          </span>
          <span className="text-xs font-normal opacity-80">{t('controls.skipHint')}</span>
        </Button>
      ) : (
        <Button
          variant="outline"
          onClick={onListenMore}
          disabled={busy || !canListenMore}
          className="h-auto min-h-11 flex-col gap-0.5 py-2"
        >
          <span className="inline-flex items-center gap-2">
            <FastForward aria-hidden="true" className="size-4" strokeWidth={2} />
            {t('controls.listenMore')}
          </span>
          <span className="num text-xs font-normal text-muted-foreground">
            {t('controls.listenMoreHint', { label: nextLabel, points: pointsForLevel(level + 1) })}
          </span>
        </Button>
      )}
    </div>
  )
}
