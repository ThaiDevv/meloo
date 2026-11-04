import { cn } from '@/shared/lib/utils'
import { Avatar } from '@/shared/ui/Avatar'
import '../lobby.css'

interface PlayerDiscProps {
  name: string
  src?: string
  /** A ready player's record turns; everyone else's rests. */
  spinning: boolean
  /** Lost connection: the record is dimmed. */
  away?: boolean
}

/**
 * A player as a record on a deck: grooves that turn when they are ready, and their own avatar as
 * the label, which stays upright so the initial can be read. Decorative; the name sits beside it.
 */
export function PlayerDisc({ name, src, spinning, away = false }: PlayerDiscProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'relative inline-flex size-20 shrink-0 items-center justify-center sm:size-24',
        away && 'opacity-40 grayscale',
      )}
    >
      <svg
        viewBox="0 0 100 100"
        className={cn('absolute inset-0 text-foreground', spinning && 'lobby-disc-spin')}
      >
        <circle cx="50" cy="50" r="50" className="fill-raised" />
        <circle
          cx="50"
          cy="50"
          r="43"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.1"
          strokeWidth="1.5"
        />
        <circle
          cx="50"
          cy="50"
          r="37"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.1"
          strokeWidth="1.5"
        />
        <circle
          cx="50"
          cy="50"
          r="31"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.1"
          strokeWidth="1.5"
        />
        <path
          d="M50 7 A43 43 0 0 1 93 50"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.28"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
        <path
          d="M50 93 A43 43 0 0 1 7 50"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.28"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
        {spinning ? (
          <circle cx="50" cy="50" r="49" fill="none" className="stroke-primary" strokeWidth="2" />
        ) : null}
      </svg>
      <Avatar name={name} src={src} className="relative size-11 text-lg sm:size-14 sm:text-2xl" />
    </span>
  )
}
