import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { addDays, localDay, monthOf, monthShortName, weekdayIndex } from '@/shared/daily'
import { cn } from '@/shared/lib/utils'
import { useShownOnce } from './useShownOnce'
import './home.css'

const WEEKS = 40
/** On a phone only the latest weeks fit; older columns are hidden. */
const PHONE_WEEKS = 14

/**
 * 0 = the day is not lit: the daily challenge was not taken, or taken but not guessed ("Chưa đoán
 * ra"). 1 to 5 = it was guessed at the 30, 20, 10, 5 or 2 second level (BR-25: the earlier the
 * level, the better the result), so the faster the guess, the brighter the cell.
 */
export type StreakLevel = 0 | 1 | 2 | 3 | 4 | 5

interface CellStyle extends CSSProperties {
  '--w': number
  '--d': number
}

interface StreakGridProps {
  /** Level of a given day (`yyyy-MM-dd`), or 0. Today is asked too: it can already be lit. */
  levelOf: (day: string) => StreakLevel
  /** Hover text of a lit day, e.g. "22/10: Đúng ở 10s". Nothing is read from it by assistive technology. */
  titleOf?: (day: string) => string | undefined
  /** The server's "today" (`yyyy-MM-dd`, BR-25). Without it the picture uses the device's day. */
  today?: string
  className?: string
}

function cellStyle(week: number, day: number): CellStyle {
  return { '--w': week, '--d': day }
}

/** Columns are weeks that start on Monday; the last one holds today, and its later days are blank. */
function buildWeeks(today: string): string[][] {
  const first = addDays(today, -weekdayIndex(today) - (WEEKS - 1) * 7)
  return Array.from({ length: WEEKS }, (_, week) =>
    Array.from({ length: 7 }, (_, day) => addDays(first, week * 7 + day)),
  )
}

/**
 * A calendar of the last nine months in the manner of a contribution graph or a streak: one cell per
 * day, lit when the daily challenge was guessed, brighter the earlier it was guessed. It lights up
 * from left to right the first time it scrolls into view. Days are `yyyy-MM-dd` strings and the
 * calendar is laid out around the server's `today`, so the device's time zone moves nothing. The
 * grid is decorative (the text around it says what it is), so it is hidden from assistive technology
 * and takes no focus.
 */
export function StreakGrid({ levelOf, titleOf, today: serverToday, className }: StreakGridProps) {
  const { i18n } = useTranslation('home')
  const [ref, shown] = useShownOnce<HTMLDivElement>(0.25)
  const language = i18n.resolvedLanguage ?? 'vi'
  const today = serverToday ?? localDay()
  const weeks = buildWeeks(today)
  const weekdayName = new Intl.DateTimeFormat(language, { weekday: 'narrow', timeZone: 'UTC' })
  // 1, 3 and 5 January 2024 were a Monday, a Wednesday and a Friday.
  const dayLabels = new Map(
    [0, 2, 4].map((day) => [day, weekdayName.format(new Date(Date.UTC(2024, 0, 1 + day)))]),
  )

  return (
    <div
      ref={ref}
      aria-hidden="true"
      data-shown={shown}
      className={cn(
        'mh-streak grid auto-cols-[minmax(0,1fr)] grid-flow-col grid-cols-[1.75rem] grid-rows-[1.5rem_repeat(7,auto)] gap-[3px] select-none sm:gap-1',
        className,
      )}
    >
      <span />
      {Array.from({ length: 7 }, (_, day) => (
        <span
          key={day}
          className="flex items-center text-sm whitespace-nowrap text-muted-foreground"
        >
          {dayLabels.get(day)}
        </span>
      ))}
      {weeks.map((week, weekIndex) => {
        const previous = weeks[weekIndex - 1]?.[0]
        const first = week[0]
        // No label on the first column: the next month starts a week or two later and the two would overlap.
        const monthLabel =
          previous !== undefined &&
          first !== undefined &&
          monthOf(previous) !== monthOf(first) &&
          weekIndex > 1
            ? monthShortName(first, language)
            : ''
        const phoneHidden = weekIndex < WEEKS - PHONE_WEEKS
        return [
          <span
            key={`m${String(weekIndex)}`}
            className={cn(
              'relative text-sm whitespace-nowrap text-muted-foreground',
              phoneHidden && 'max-sm:hidden',
            )}
          >
            {monthLabel}
          </span>,
          ...week.map((day, dayIndex) => {
            const isToday = day === today
            const future = day > today
            const level = future ? 0 : levelOf(day)
            return (
              <span
                key={day}
                data-level={future ? undefined : level}
                data-today={isToday ? 'true' : undefined}
                data-day={future ? undefined : day}
                title={level > 0 && !future ? titleOf?.(day) : undefined}
                style={cellStyle(weekIndex, dayIndex)}
                className={cn(
                  'mh-cell relative aspect-square rounded-[3px] sm:rounded-[4px]',
                  future && 'invisible',
                  phoneHidden && 'max-sm:hidden',
                )}
              />
            )
          }),
        ]
      })}
    </div>
  )
}
