/** A failed admin call, with the HTTP status and the problem `code` when the server sent one. */
export class AdminApiError extends Error {
  readonly status: number
  readonly code: string | undefined

  constructor(operation: string, status: number, problem: unknown) {
    const code =
      typeof problem === 'object' &&
      problem !== null &&
      'code' in problem &&
      typeof problem.code === 'string'
        ? problem.code
        : undefined
    super(`${operation} failed: ${String(status)}${code ? ` ${code}` : ''}`)
    this.status = status
    this.code = code
  }
}

/** i18n key (namespace admin) for an error shown after a failed admin call. */
export function adminErrorKey(error: unknown): string {
  if (!(error instanceof AdminApiError)) return 'errors.generic'
  if (error.code === 'NOT_ENOUGH_TRACKS') return 'errors.notEnoughTracks'
  if (error.status === 409) return 'errors.conflict'
  if (error.status === 404) return 'errors.notFound'
  if (error.status === 400) return 'errors.invalid'
  if (error.status === 403) return 'errors.forbidden'
  return 'errors.generic'
}
