/**
 * The device clock's distance from the server's, learned from the `serverTime` of every response.
 * The countdown to the next challenge uses it, so a wrong device clock cannot move the deadline.
 */
let offsetMs = 0

export function noteServerTime(serverTime: string): void {
  const parsed = Date.parse(serverTime)
  if (!Number.isNaN(parsed)) offsetMs = parsed - Date.now()
}

export function serverNow(): number {
  return Date.now() + offsetMs
}

/** "05:07:09" */
export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000))
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(Math.floor(total / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`
}
