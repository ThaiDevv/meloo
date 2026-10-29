import { useEffect, useRef, useState, type ReactNode } from 'react'
import { cn } from '@/shared/lib/utils'

interface RevealProps {
  /** Milliseconds before the entrance starts, to stagger the parts of one section. */
  delay?: number
  className?: string
  children: ReactNode
}

/**
 * Lets its content rise in once, the first time it scrolls into view. Without IntersectionObserver
 * (old browsers, tests) the content is shown at once; under reduced motion it never travels.
 */
export function Reveal({ delay = 0, className, children }: RevealProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [shown, setShown] = useState(() => typeof IntersectionObserver === 'undefined')

  useEffect(() => {
    const element = ref.current
    if (shown || !element) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          setShown(true)
          observer.disconnect()
        }
      },
      { threshold: 0.15 },
    )
    observer.observe(element)
    return () => {
      observer.disconnect()
    }
  }, [shown])

  return (
    <div
      ref={ref}
      style={{ animationDelay: `${String(delay)}ms` }}
      className={cn(
        shown
          ? 'animate-in fade-in slide-in-from-bottom-6 duration-500 fill-mode-both motion-reduce:animate-none'
          : 'opacity-0 motion-reduce:opacity-100',
        className,
      )}
    >
      {children}
    </div>
  )
}
