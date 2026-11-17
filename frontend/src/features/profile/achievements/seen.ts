const memory = new Map<string, Map<string, number>>()
const key = (userId: string) => `meloo:achievements:seen:${userId}`

export function markSeen(
  userId: string,
  items: readonly { code: string; unlocked: boolean; unlockedAt?: string | null }[],
) {
  const seen = readSeen(userId) ?? new Map<string, number>()
  for (const item of items) {
    const stamp = item.unlocked && item.unlockedAt ? Date.parse(item.unlockedAt) : NaN
    if (Number.isFinite(stamp))
      seen.set(item.code, Math.max(stamp, seen.get(item.code) ?? -Infinity))
  }
  writeSeen(userId, seen)
}

/** Server unlock timestamps, partitioned by identity; never a browser-clock cutoff. */
export function readSeen(userId: string): Map<string, number> | null {
  const cached = memory.get(userId)
  if (cached) return new Map(cached)
  try {
    const raw = localStorage.getItem(key(userId))
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return null
    return new Map(
      Object.entries(parsed).flatMap(([code, stamp]) =>
        typeof stamp === 'number' && Number.isFinite(stamp) ? [[code, stamp]] : [],
      ),
    )
  } catch {
    return null
  }
}

export function writeSeen(userId: string, seen: Map<string, number>) {
  memory.set(userId, new Map(seen))
  try {
    localStorage.setItem(key(userId), JSON.stringify(Object.fromEntries(seen)))
  } catch {
    // Storage can be unavailable; the in-memory snapshot still prevents repeats this session.
  }
}
