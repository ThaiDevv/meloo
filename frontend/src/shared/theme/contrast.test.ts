import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { contrastRatio } from './contrast'

const css = readFileSync(path.resolve(import.meta.dirname, 'theme.css'), 'utf8')

/** Reads the `--token: #hex;` declarations of one block (`:root` or `.dark`) of theme.css. */
function tokens(selector: ':root' | '.dark'): Record<string, string> {
  const block = new RegExp(`^${selector.replace('.', '\\.')} \\{([^}]*)\\}`, 'm').exec(css)?.[1]
  if (!block) throw new Error(`theme.css has no ${selector} block`)
  return Object.fromEntries(
    [...block.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6});/g)].map((m): [string, string] => [
      m[1] ?? '',
      m[2] ?? '',
    ]),
  )
}

// [foreground token, background token, minimum ratio]. 4.5 for text (AA), 3 for UI parts (AA 1.4.11).
const pairs: [string, string, number][] = [
  ['foreground', 'background', 4.5],
  ['foreground', 'surface', 4.5],
  ['muted-foreground', 'background', 4.5],
  ['muted-foreground', 'surface', 4.5],
  ['muted-foreground', 'raised', 4.5],
  ['faint', 'background', 4.5],
  ['faint', 'surface', 4.5],
  ['faint', 'raised', 4.5],
  ['primary-foreground', 'primary', 4.5],
  ['primary', 'background', 4.5],
  ['primary', 'surface', 4.5],
  ['positive', 'positive-soft', 4.5],
  ['negative', 'negative-soft', 4.5],
  ['negative', 'surface', 4.5],
  ['warning', 'warning-soft', 4.5],
  ['control', 'background', 3],
  ['control', 'surface', 3],
  ['ring', 'background', 3],
  ['ring', 'surface', 3],
]

describe.each([
  ['dark', '.dark'],
  ['light', ':root'],
] as const)('%s theme contrast (WCAG AA)', (_name, selector) => {
  const t = tokens(selector)
  it.each(pairs)('%s on %s is at least %s:1', (fg, bg, min) => {
    const [a, b] = [t[fg], t[bg]]
    if (!a || !b) throw new Error(`missing token ${fg} or ${bg}`)
    expect(contrastRatio(a, b)).toBeGreaterThanOrEqual(min)
  })
})
