import type { TFunction } from 'i18next'
import type { RoundResult } from '@/features/game-core'
import type { MatchResult } from '../api/soloMatchApi'
import { longestCombo } from '../model/matchState'

const LENGTHS = [2, 5, 10, 20, 30] as const

/** "2s", "5s" … (glossary: the same short label in every language). */
export function levelLabel(level: number): string {
  return `${String(LENGTHS[level - 1] ?? 30)}s`
}

/** "Right at 5s", "Not guessed", … from what the server said about the round. */
export function outcomeText(round: RoundResult, t: TFunction<'solo'>): string {
  return t(`play.summary.outcome.${round.outcome}`, { label: levelLabel(round.correctLevel ?? 5) })
}

/**
 * The text a player shares: score, songs right, longest combo and the level of each round. Song
 * titles are left out, so a friend who plays the same standard set is not handed the answers.
 */
export function shareText(result: MatchResult, t: TFunction<'solo'>, language: string): string {
  const number = new Intl.NumberFormat(language)
  const lines = [
    t(`play.share.headline.${result.mode}`),
    t('play.share.score', {
      score: number.format(result.totalScore),
      correct: result.correctCount,
      total: result.roundCount,
      combo: longestCombo(result),
    }),
    ...result.rounds.map((round) =>
      t('play.share.round', { n: round.roundNo, outcome: outcomeText(round, t) }),
    ),
    t('play.share.invite'),
  ]
  return lines.join('\n')
}

export type ShareOutcome = 'shared' | 'copied' | 'cancelled' | 'failed'

/** The system share sheet where there is one, the clipboard otherwise. */
export async function shareResult(input: {
  title: string
  text: string
  url: string
}): Promise<ShareOutcome> {
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
    await navigator.clipboard.writeText(`${input.text}\n${input.url}`)
    return 'copied'
  } catch {
    return 'failed'
  }
}
