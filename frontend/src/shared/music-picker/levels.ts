/** Seconds between one listening level lighting up and the next, shared by the disc and ladders. */
export const LEVEL_STEP_SECONDS = 0.7

/** animation-delay for the n-th level, so a ladder next to the disc lights up in step with it. */
export function levelDelay(index: number): string {
  return `${String(index * LEVEL_STEP_SECONDS)}s`
}
