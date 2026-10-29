import { useEffect, useRef, useState, type RefObject } from 'react'

/**
 * True once the element has scrolled into view, and for good after that. Without
 * IntersectionObserver (old browsers, tests) it is true from the start, so nothing stays hidden.
 */
export function useShownOnce<T extends Element>(threshold = 0.15): [RefObject<T | null>, boolean] {
  const ref = useRef<T>(null)
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
      { threshold },
    )
    observer.observe(element)
    return () => {
      observer.disconnect()
    }
  }, [shown, threshold])

  return [ref, shown]
}
