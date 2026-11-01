import { afterEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import resultExample from '../../../../../../contracts/examples/game/get-match-result.200.json'
import type { MatchResult } from '../api/soloMatchApi'
import { levelLabel, shareResult, shareText } from './share'

const result = resultExample as MatchResult
const t = i18n.getFixedT('vi', 'solo')

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('shareText', () => {
  it('gives the score, songs right, longest combo and the level of every round', () => {
    expect(shareText(result, t, 'vi')).toBe(
      [
        'Meloo · Chơi đơn xếp hạng',
        '3.500 điểm · đúng 4/5 bài · combo dài nhất 2',
        'Vòng 1: Đúng ở 5s',
        'Vòng 2: Đúng ở 2s',
        'Vòng 3: Bỏ qua',
        'Vòng 4: Đúng ở 10s',
        'Vòng 5: Đúng ở 2s',
        'Thử vượt mình:',
      ].join('\n'),
    )
  })

  it('never names a song or an artist', () => {
    const text = shareText(result, t, 'vi')
    for (const round of result.rounds) {
      expect(text).not.toContain(round.answer.title)
      expect(text).not.toContain(round.answer.artistName)
    }
  })

  it('speaks English with English separators', () => {
    const text = shareText(result, i18n.getFixedT('en', 'solo'), 'en')
    expect(text).toContain('3,500 points · 4/5 songs right · longest combo 2')
    expect(text).toContain('Round 3: Skipped')
  })
})

describe('levelLabel', () => {
  it('labels the five levels the glossary way', () => {
    expect([1, 2, 3, 4, 5].map(levelLabel)).toEqual(['2s', '5s', '10s', '20s', '30s'])
  })
})

describe('shareResult', () => {
  const input = { title: 'T', text: 'body', url: 'https://meloo.test/solo' }

  it('uses the share sheet when there is one', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { share, clipboard: { writeText: vi.fn() } })
    expect(await shareResult(input)).toBe('shared')
    expect(share).toHaveBeenCalledWith(input)
  })

  it('treats a dismissed share sheet as cancelled', async () => {
    const share = vi.fn().mockRejectedValue(new DOMException('no', 'AbortError'))
    vi.stubGlobal('navigator', { share, clipboard: { writeText: vi.fn() } })
    expect(await shareResult(input)).toBe('cancelled')
  })

  it('copies text and link when there is no share sheet', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    expect(await shareResult(input)).toBe('copied')
    expect(writeText).toHaveBeenCalledWith('body\nhttps://meloo.test/solo')
  })

  it('reports a failure when nothing works', async () => {
    vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new Error()) } })
    expect(await shareResult(input)).toBe('failed')
  })
})
