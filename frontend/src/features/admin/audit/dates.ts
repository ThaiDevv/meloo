/** datetime-local is the admin's local time; API boundaries are UTC instants. */
export function toUtc(value: string) {
  return value ? new Date(value).toISOString() : undefined
}
