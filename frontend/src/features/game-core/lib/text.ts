/** Lower-cases and strips accents, so "chung ta" finds "Chúng Ta". */
export function foldDiacritics(value: string): string {
  return value
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
}

export interface TextPart {
  text: string
  match: boolean
}

/**
 * Splits `text` so the bit that matches `query` (ignoring case and accents) can be emphasised.
 * Works one character at a time because folding can change the length of a string.
 */
export function splitMatch(text: string, query: string): TextPart[] {
  const needle = foldDiacritics(query.trim())
  if (!needle) return [{ text, match: false }]

  const chars = Array.from(text)
  const folded = chars.map((char) => foldDiacritics(char))
  const at = folded.join('').indexOf(needle)
  if (at < 0) return [{ text, match: false }]

  let start = 0
  for (let offset = 0; start < chars.length && offset < at; start++) {
    offset += folded[start]?.length ?? 0
  }
  let end = start
  for (let covered = 0; end < chars.length && covered < needle.length; end++) {
    covered += folded[end]?.length ?? 0
  }

  return [
    { text: chars.slice(0, start).join(''), match: false },
    { text: chars.slice(start, end).join(''), match: true },
    { text: chars.slice(end).join(''), match: false },
  ].filter((part) => part.text !== '')
}
