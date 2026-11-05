import { useTranslation } from 'react-i18next'
import { levelLabel } from '../model/format'
import type { Guessed } from '../model/matchState'

/** "X đúng ở 5s": announces each right guess of the round, to screen readers as well. */
export function GuessFeed({ guessed, me }: { guessed: readonly Guessed[]; me: string | null }) {
  const { t } = useTranslation('room')
  const others = guessed.filter((item) => item.userId !== me).slice(-3)
  return (
    <ul role="log" aria-live="polite" className="min-h-6 space-y-1 text-sm empty:hidden">
      {others.map((item) => (
        <li key={item.userId} className="text-positive">
          {t('match.feed.guessed', { name: item.nickname, label: levelLabel(item.level) })}
        </li>
      ))}
    </ul>
  )
}
