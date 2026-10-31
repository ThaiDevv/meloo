import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import type { RoundTimerState } from '../types'
import '../game-core.css'

interface RoundTimerProps {
  timer: RoundTimerState
  /** Fired once when the clock reaches zero. The server still decides what a late guess is worth (BR-07). */
  onTimeout?: () => void
  className?: string
}

const TICK_MS = 100

/**
 * Counts down the length of the round (`endsAt` minus `serverTime`) on this device's own clock from
 * the moment it appears, so a wrong device clock cannot move the deadline. Network delay is not
 * subtracted: the server judges a late guess (BR-07), this clock is only a guide.
 */
export function RoundTimer({ timer, onTimeout, className }: RoundTimerProps) {
  const { t } = useTranslation('game')
  const { endsAt, serverTime } = timer
  const totalMs = Math.max(0, Date.parse(endsAt) - Date.parse(serverTime))
  const [remaining, setRemaining] = useState(totalMs)
  const fired = useRef(false)
  const onTimeoutRef = useRef(onTimeout)

  useEffect(() => {
    onTimeoutRef.current = onTimeout
  })

  useEffect(() => {
    const localStart = Date.now()
    fired.current = false
    const tick = () => {
      const left = Math.max(0, totalMs - (Date.now() - localStart))
      setRemaining(left)
      if (left === 0 && !fired.current) {
        fired.current = true
        onTimeoutRef.current?.()
      }
    }
    tick()
    const id = window.setInterval(tick, TICK_MS)
    return () => {
      window.clearInterval(id)
    }
  }, [endsAt, serverTime, totalMs])

  const seconds = Math.ceil(remaining / 1000)
  const share = totalMs > 0 ? remaining / totalMs : 0
  const urgent = seconds <= 10

  return (
    <div
      role="timer"
      aria-label={t('timer.label')}
      aria-valuetext={t('timer.value', { seconds })}
      className={cn('flex items-center gap-3', className)}
    >
      <div
        aria-hidden="true"
        className="h-1.5 flex-1 overflow-hidden rounded-full bg-foreground/15"
      >
        <div
          className={cn('gc-timer-fill h-full rounded-full', urgent ? 'bg-negative' : 'bg-primary')}
          style={{ transform: `scaleX(${String(share)})` }}
        />
      </div>
      <span
        aria-hidden="true"
        className={cn('num w-14 text-right text-lg font-semibold', urgent && 'text-negative')}
      >
        {formatClock(seconds)}
      </span>
    </div>
  )
}

function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const rest = totalSeconds % 60
  return `${String(minutes)}:${String(rest).padStart(2, '0')}`
}
