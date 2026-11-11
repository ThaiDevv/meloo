/** Seconds of clip each guessing level opens (BR: 2 → 5 → 10 → 20 → 30 seconds). */
const LEVEL_SECONDS = [2, 5, 10, 20, 30] as const

/** The clip length of a level 1..5, or null outside that range. */
export function secondsForLevel(level: number): number | null {
  if (!Number.isInteger(level)) return null
  return LEVEL_SECONDS[level - 1] ?? null
}

/** A page number read from `?page=` (1-based in the URL, 0-based for the API); bad values fall back to 0. */
export function pageIndexFromParam(value: string | null): number {
  if (value === null) return 0
  const n = Number(value)
  return Number.isInteger(n) && n >= 1 ? n - 1 : 0
}
