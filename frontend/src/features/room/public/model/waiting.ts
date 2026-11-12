/** After this long alone in the queue the player is offered other ways to play (BR-13). */
export const ALONE_HINT_AFTER_S = 60

/** 75 -> "1:15": the time the player has waited. */
export function formatElapsed(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes)}:${String(seconds).padStart(2, '0')}`
}
