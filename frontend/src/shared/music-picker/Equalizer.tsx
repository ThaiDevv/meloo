import { cn } from '@/shared/lib/utils'
import './visuals.css'

interface EqualizerProps {
  /** Freezes the bars where they are, e.g. for something that is not playing yet. */
  paused?: boolean
  className?: string
}

/** Five bouncing bars in the current text colour. Sized by font-size: it is 1em tall. */
export function Equalizer({ paused = false, className }: EqualizerProps) {
  return (
    <span aria-hidden="true" data-paused={paused} className={cn('mv-eq', className)}>
      {[0, 1, 2, 3, 4].map((bar) => (
        <i key={bar} />
      ))}
    </span>
  )
}
