/** "5s": how the glossary writes a listening level. */
export function levelLabel(seconds: number): string {
  return `${String(seconds)}s`
}

export function signed(value: number, language: string): string {
  const text = new Intl.NumberFormat(language).format(Math.abs(value))
  if (value > 0) return `+${text}`
  if (value < 0) return `−${text}`
  return text
}
