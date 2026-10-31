/** Scores use the reader's thousands separator: 1.000 in Vietnamese, 1,000 in English. */
export function formatScore(value: number, language: string): string {
  return new Intl.NumberFormat(language).format(value)
}

/** "DEEZER" -> "Deezer". */
export function providerName(provider: string): string {
  return provider.charAt(0).toUpperCase() + provider.slice(1).toLowerCase()
}
