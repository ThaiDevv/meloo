import { describe, expect, it } from 'vitest'
import { foldDiacritics, splitMatch } from './text'

describe('foldDiacritics', () => {
  it('strips Vietnamese accents, the đ and case', () => {
    expect(foldDiacritics('Chúng Ta Của Hiện Tại')).toBe('chung ta cua hien tai')
    expect(foldDiacritics('Đen Vâu')).toBe('den vau')
  })
})

describe('splitMatch', () => {
  it('finds the match without accents or case and keeps the original text', () => {
    expect(splitMatch('Chúng Ta Của Hiện Tại', 'chung ta')).toEqual([
      { text: 'Chúng Ta', match: true },
      { text: ' Của Hiện Tại', match: false },
    ])
  })

  it('matches in the middle', () => {
    expect(splitMatch('Sơn Tùng M-TP', 'tung')).toEqual([
      { text: 'Sơn ', match: false },
      { text: 'Tùng', match: true },
      { text: ' M-TP', match: false },
    ])
  })

  it('returns the whole text when nothing matches or the query is blank', () => {
    expect(splitMatch('Hello', 'xyz')).toEqual([{ text: 'Hello', match: false }])
    expect(splitMatch('Hello', '  ')).toEqual([{ text: 'Hello', match: false }])
  })
})
