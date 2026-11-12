/**
 * Calendar days as `yyyy-MM-dd` strings, the way the server names a challenge day (Asia/Ho_Chi_Minh).
 * Everything here works on the string and on UTC arithmetic, so the time zone of the device never
 * moves a day (BR-25).
 */

const ISO_DAY = /^(\d{4})-(\d{2})-(\d{2})$/

function toUtc(iso: string): Date {
  const match = ISO_DAY.exec(iso)
  if (!match) throw new Error(`Not a yyyy-MM-dd date: ${iso}`)
  return new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])))
}

function fromUtc(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function addDays(iso: string, days: number): string {
  const date = toUtc(iso)
  date.setUTCDate(date.getUTCDate() + days)
  return fromUtc(date)
}

/** 0 = Monday … 6 = Sunday. */
export function weekdayIndex(iso: string): number {
  return (toUtc(iso).getUTCDay() + 6) % 7
}

/** The device's own calendar day, for screens that have no server day yet (the home page picture). */
export function localDay(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${String(date.getFullYear())}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** "22/10" in Vietnamese, "10/22" in English; the same on every device. */
export function formatDayMonth(iso: string, language: string): string {
  const [, month = '', day = ''] = iso.split('-')
  return language.startsWith('en') ? `${month}/${day}` : `${day}/${month}`
}

/** "22/10/2026" in every language: the share text must read the same wherever it lands. */
export function formatDayMonthYear(iso: string): string {
  const [year, month, day] = iso.split('-')
  return `${day ?? ''}/${month ?? ''}/${year ?? ''}`
}

export function monthShortName(iso: string, language: string): string {
  return new Intl.DateTimeFormat(language, { month: 'short', timeZone: 'UTC' }).format(toUtc(iso))
}

export function monthOf(iso: string): number {
  return toUtc(iso).getUTCMonth()
}

/** Brightness of a solved day: the 30 second level is the faintest (1), the 2 second level the brightest (5). */
export function brightnessOf(listenSeconds: number): 1 | 2 | 3 | 4 | 5 {
  switch (listenSeconds) {
    case 2:
      return 5
    case 5:
      return 4
    case 10:
      return 3
    case 20:
      return 2
    default:
      return 1
  }
}
