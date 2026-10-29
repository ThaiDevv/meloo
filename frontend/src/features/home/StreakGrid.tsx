import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
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
  /** Level of a given day, or 0. Today can be left out: it is drawn as "waiting to be lit". */
  levelOf: (date: Date) => StreakLevel
  className?: string
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function sameDay(a: Date, b: Date): boolean {
  return a.getTime() === b.getTime()
}

function cellStyle(week: number, day: number): CellStyle {
  return { '--w': week, '--d': day }
}

/** Columns are weeks that start on Monday; the last one holds today, and its later days are blank. */
function buildWeeks(today: Date): Date[][] {
  const mondayIndex = (today.getDay() + 6) % 7
  const first = new Date(today)
  first.setDate(today.getDate() - mondayIndex - (WEEKS - 1) * 7)
  return Array.from({ length: WEEKS }, (_, week) =>
    Array.from({ length: 7 }, (_, day) => {
      const date = new Date(first)
      date.setDate(first.getDate() + week * 7 + day)
      return date
    }),
  )
}

/**
 * A calendar of the last nine months in the manner of a contribution graph or a streak: one cell per
 * day, lit when the daily challenge was played, brighter the earlier it was guessed. It lights up
 * from left to right the first time it scrolls into view. Decorative (the text around it says what
 * it is), so it is hidden from assistive technology.
 */
export function StreakGrid({ levelOf, className }: StreakGridProps) {
  const { i18n } = useTranslation('home')
  const [ref, shown] = useShownOnce<HTMLDivElement>(0.25)
  const language = i18n.resolvedLanguage ?? 'vi'
  const today = startOfDay(new Date())
  const weeks = buildWeeks(today)
  const monthName = new Intl.DateTimeFormat(language, { month: 'short' })
  const weekdayName = new Intl.DateTimeFormat(language, { weekday: 'narrow' })
  // 1, 3 and 5 January 2024 were a Monday, a Wednesday and a Friday.
  const dayLabels = new Map([
    [0, weekdayName.format(new Date(2024, 0, 1))],
    [2, weekdayName.format(new Date(2024, 0, 3))],
    [4, weekdayName.format(new Date(2024, 0, 5))],
  ])

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
          previous.getMonth() !== first.getMonth() &&
          weekIndex > 1
            ? monthName.format(first)
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
          ...week.map((date, dayIndex) => {
            const isToday = sameDay(date, today)
            const future = date.getTime() > today.getTime()
            const level = future || isToday ? 0 : levelOf(date)
            return (
              <span
                key={`${String(weekIndex)}-${String(dayIndex)}`}
                data-level={future ? undefined : level}
                data-today={isToday ? 'true' : undefined}
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
