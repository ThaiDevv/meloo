import type { CSSProperties } from 'react'

/**
 * Entrance of the n-th part of a section: with the `motion-rise` class, parts come in one after
 * another, 70 ms apart (reduced motion shows them at once, see shared/theme/theme.css).
 */
export function rise(index: number): CSSProperties {
  return { animationDelay: `${String(index * 70)}ms` }
}
