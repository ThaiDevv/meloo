import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatCountdown, serverNow } from '../lib/clock'

/** Time to the next challenge (00:00 in Vietnam). Counts on the server's clock and calls `onReached` once at zero. */
export function Countdown({
  nextResetAt,
  onReached,
}: {
  nextResetAt: string
  onReached: () => void
}) {
  const { t } = useTranslation('daily')
  const target = Date.parse(nextResetAt)
  const [left, setLeft] = useState(() => target - serverNow())
  const fired = useRef(false)
  const onReachedRef = useRef(onReached)

  useEffect(() => {
    onReachedRef.current = onReached
  })

  useEffect(() => {
    fired.current = false
    const tick = () => {
      const remaining = target - serverNow()
      setLeft(remaining)
      if (remaining <= 0 && !fired.current) {
        fired.current = true
        onReachedRef.current()
      }
    }
    tick()
    const id = window.setInterval(tick, 1000)
    return () => {
      window.clearInterval(id)
    }
  }, [target])

  return (
    <p className="flex flex-col gap-1" data-testid="daily-countdown">
      <span className="text-sm text-muted-foreground">{t('finished.nextIn')}</span>
      <time className="num font-display text-4xl font-extrabold tracking-tight" aria-live="off">
        {formatCountdown(left)}
      </time>
    </p>
  )
}
