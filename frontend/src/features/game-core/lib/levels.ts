export const LEVEL_COUNT = 5

/** BR-02: 1000 / 800 / 600 / 400 / 200 for a correct guess at level 1..5. */
export function pointsForLevel(level: number): number {
  return Math.max(0, 1200 - 200 * level)
}

/** Short UI label of a level length (glossary: 2s, 5s, ...), the same in every language. */
export function levelLabel(listenSeconds: number): string {
  return `${String(listenSeconds)}s`
}
