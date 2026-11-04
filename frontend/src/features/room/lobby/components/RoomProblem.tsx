import { TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'

export type RoomProblemKind =
  'invalid-code' | 'not-found' | 'full' | 'kicked' | 'locked' | 'rate-limited' | 'unknown'

interface RoomProblemProps {
  kind: RoomProblemKind
  /** Offered for the problems that may pass (busy server, slow network). */
  onRetry?: () => void
}

/** Why the player is not in the room, and the one or two things they can do next. */
export function RoomProblem({ kind, onRetry }: RoomProblemProps) {
  const { t } = useTranslation('room')
  const canRetry = kind === 'rate-limited' || kind === 'unknown'
  return (
    <div className="mx-auto flex w-full max-w-lg flex-col items-center gap-5 px-4 py-20 text-center">
      <TriangleAlert aria-hidden="true" className="size-6 text-negative" strokeWidth={1.75} />
      <div role="alert" className="space-y-2">
        <h1 className="font-display text-2xl font-extrabold tracking-[-0.02em]">
          {t(`problem.${kind}.title`)}
        </h1>
        <p className="text-muted-foreground">{t(`problem.${kind}.description`)}</p>
      </div>
      <div className="flex flex-wrap justify-center gap-2">
        {canRetry && onRetry ? <Button onClick={onRetry}>{t('problem.retry')}</Button> : null}
        <Button asChild variant={canRetry ? 'outline' : 'default'}>
          <Link to="/rooms/new">{t('problem.createRoom')}</Link>
        </Button>
        <Button asChild variant="ghost">
          <Link to="/">{t('problem.home')}</Link>
        </Button>
      </div>
    </div>
  )
}
