import { useTranslation } from 'react-i18next'
import type { Standing } from '../model/standings'
import { PlayerCard } from './PlayerCard'

/** The live table. The leader comes first and is outlined. */
export function Scoreboard({ standings }: { standings: readonly Standing[] }) {
  const { t } = useTranslation('room')
  return (
    <section aria-labelledby="match-scoreboard" className="space-y-3">
      <h2 id="match-scoreboard" className="text-sm font-semibold text-muted-foreground">
        {t('match.scoreboard')}
      </h2>
      <ul className="grid grid-cols-1 gap-2 max-sm:grid-cols-2 sm:grid-cols-2 lg:grid-cols-1">
        {standings.map((player, index) => (
          <PlayerCard
            key={player.userId}
            player={player}
            leader={index === 0 && standings.length > 1 && player.score > 0}
          />
        ))}
      </ul>
    </section>
  )
}
