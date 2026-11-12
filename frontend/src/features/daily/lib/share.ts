import type { TFunction } from 'i18next'
import { formatDayMonthYear } from '@/shared/daily'
import type { DailyResult } from '../api/dailyApi'

const SQUARE = { CORRECT: '🟩', WRONG: '🟥', UNOPENED: '⬜' } as const

/** "Right at 10s" or "Not guessed", from the language of the interface (BR-25). */
export function levelNote(result: DailyResult, t: TFunction<'daily'>): string {
  return result.status === 'SOLVED' && result.correctListenSeconds
    ? t('share.correct', { label: `${String(result.correctListenSeconds)}s` })
    : t('share.unsolved')
}

/**
 * The text a player shares (BR-25): the day, five coloured squares with the level note, and the link.
 * Built from the result alone, so it carries neither the song title nor the artist.
 */
export function dailyShareText(result: DailyResult, t: TFunction<'daily'>, url: string): string {
  return [
    t('share.headline', { date: formatDayMonthYear(result.challengeDate) }),
    `${result.levels.map((level) => SQUARE[level]).join('')} ${levelNote(result, t)}`,
    url,
  ].join('\n')
}

export type ShareOutcome = 'shared' | 'copied' | 'cancelled' | 'failed'

/** The system share sheet where there is one (phones), the clipboard otherwise. */
export async function shareDaily(input: { title: string; text: string }): Promise<ShareOutcome> {
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share(input)
      return 'shared'
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled'
      // Fall through to the clipboard: some browsers refuse share() for reasons the player cannot fix.
    }
  }
  try {
    await navigator.clipboard.writeText(input.text)
    return 'copied'
  } catch {
    return 'failed'
  }
}
