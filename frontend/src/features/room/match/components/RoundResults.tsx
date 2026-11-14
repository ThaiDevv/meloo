import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Avatar } from '@/shared/ui/Avatar'
import { levelLabel } from '../model/format'
import type { Reveal } from '../model/matchState'
import type { Standing } from '../model/standings'

/** Every player's points for the round, split into base, combo and speed (BR-02, BR-07, BR-08). */
export function RoundResults({
  reveal,
  standings,
}: {
  reveal: Reveal
  standings: readonly Standing[]
}) {
  const { t, i18n } = useTranslation(['room', 'game'])
  const fmt = (value: number) => new Intl.NumberFormat(i18n.language).format(value)
  const rows = [...reveal.results].sort((a, b) => b.roundScore - a.roundScore)
  return (
    <section aria-labelledby="match-results" className="space-y-3">
      <h2 id="match-results" className="font-display text-xl font-extrabold tracking-tight">
        {t('room:match.result.heading', { n: reveal.roundNo })}
      </h2>
      <div className="overflow-x-auto rounded-2xl bg-surface">
        <table className="w-full min-w-[32rem] text-sm">
          <thead className="text-left text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">
                {t('room:match.result.player')}
              </th>
              <th scope="col" className="px-2 py-3 text-right font-medium">
                {t('room:match.result.base')}
              </th>
              <th scope="col" className="px-2 py-3 text-right font-medium">
                {t('room:match.result.combo')}
              </th>
              <th scope="col" className="px-2 py-3 text-right font-medium">
                {t('room:match.result.speed')}
              </th>
              <th scope="col" className="px-4 py-3 text-right font-medium">
                {t('room:match.result.round')}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const who = standings.find((player) => player.userId === row.userId)
              const name = who?.nickname ?? row.userId
              return (
                <tr key={row.userId} className="border-t border-foreground/10">
                  <th scope="row" className="px-4 py-3 text-left font-normal">
                    <span className="flex items-center gap-2">
                      <Avatar
                        name={name}
                        src={who?.avatarUrl ?? undefined}
                        avatarId={who?.avatarId}
                        size="sm"
                      />
                      <span className="min-w-0">
                        <span className="block truncate font-semibold">{name}</span>
                        <span className="block text-xs text-muted-foreground">
                          {t(`game:reveal.outcome.${row.outcome}`, {
                            label: levelLabel(row.correctLevel ?? 0),
                          })}
                        </span>
                      </span>
                    </span>
                  </th>
                  <td className="num px-2 py-3 text-right">{fmt(row.baseScore)}</td>
                  <td className="num px-2 py-3 text-right">
                    {row.comboPercent > 0 ? `+${String(row.comboPercent)}%` : '—'}
                  </td>
                  <td className="num px-2 py-3 text-right">
                    {row.speedBonus > 0 ? `+${fmt(row.speedBonus)}` : '—'}
                  </td>
                  <td className="num px-4 py-3 text-right text-base font-bold">
                    {fmt(row.roundScore)}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}

/** Counts down the pause before the next round, on this device's clock from the moment it shows. */
export function NextRoundCountdown({ reveal }: { reveal: Reveal }) {
  const { t } = useTranslation('room')
  const total = reveal.nextRoundAt
    ? Math.max(0, Date.parse(reveal.nextRoundAt) - Date.parse(reveal.serverTime))
    : null
  const [left, setLeft] = useState(total)
  useEffect(() => {
    if (total === null) return undefined
    const start = Date.now()
    const id = window.setInterval(() => {
      setLeft(Math.max(0, total - (Date.now() - start)))
    }, 250)
    return () => {
      window.clearInterval(id)
    }
  }, [total])
  if (left === null)
    return <p className="text-sm text-muted-foreground">{t('match.result.last')}</p>
  return (
    <p role="timer" className="num text-sm text-muted-foreground">
      {t('match.result.next', { n: Math.ceil(left / 1000) })}
    </p>
  )
}
