import { useEffect, useRef, useState } from 'react'

/**
 * Whether things may travel on screen. False under prefers-reduced-motion, and false where the
 * browser cannot answer (tests): values then appear at once, which is also what a reader of the
 * DOM expects.
 */
export function motionAllowed(): boolean {
  return (
    typeof window !== 'undefined' &&
    // jsdom declares matchMedia but leaves it undefined.
    typeof (window.matchMedia as unknown) === 'function' &&
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/** Calls `frame` on every animation frame while `active` is true. */
export function useFrameLoop(active: boolean, frame: (time: number) => void): void {
  const frameRef = useRef(frame)
  useEffect(() => {
    frameRef.current = frame
  })
  useEffect(() => {
    if (!active || !('requestAnimationFrame' in window)) return
    let id = window.requestAnimationFrame(function loop(time) {
      frameRef.current(time)
      id = window.requestAnimationFrame(loop)
    })
    return () => {
      window.cancelAnimationFrame(id)
    }
  }, [active])
}

/**
 * A whole number that runs to `value` (ease-out) instead of jumping, e.g. points dropping from
 * 800 to 600. It starts at `from` when given. Without motion it is simply `value`.
 */
export function useCountTo(value: number, { from, ms = 600 }: { from?: number; ms?: number } = {}) {
  const animate = motionAllowed()
  const [shown, setShown] = useState(from ?? value)
  const shownRef = useRef(from ?? value)

  useEffect(() => {
    if (!animate) return
    const start = shownRef.current
    if (start === value) return
    const began = performance.now()
    let id = window.requestAnimationFrame(function step(now) {
      const progress = Math.min(1, (now - began) / ms)
      const next = Math.round(start + (value - start) * (1 - (1 - progress) ** 3))
      shownRef.current = next
      setShown(next)
      if (progress < 1) id = window.requestAnimationFrame(step)
    })
    return () => {
      window.cancelAnimationFrame(id)
    }
  }, [value, animate, ms])

  return animate ? shown : value
}
