import type { TFunction } from 'i18next'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import resultExample from '../../../../../contracts/examples/daily/get-daily-result.200.json'
import type { DailyResult } from '../api/dailyApi'
import { dailyShareText, shareDaily } from './share'

const URL = 'https://meloo.example/daily'
const solved = resultExample as DailyResult
const tDaily = (): TFunction<'daily'> => i18n.getFixedT(null, 'daily')

describe('dailyShareText (BR-25)', () => {
  it('builds the day, the five squares with the level note and the link', () => {
    const result: DailyResult = {
      ...solved,
      challengeDate: '2026-10-22',
      correctLevel: 3,
      correctListenSeconds: 10,
      levels: ['WRONG', 'WRONG', 'CORRECT', 'UNOPENED', 'UNOPENED'],
    }
    expect(dailyShareText(result, tDaily(), URL)).toBe(
      'Meloo · 22/10/2026\n🟥🟥🟩⬜⬜ Đúng ở 10s\nhttps://meloo.example/daily',
    )
  })

  it('says "Chưa đoán ra" when the song was not guessed', () => {
    const result: DailyResult = {
      ...solved,
      status: 'UNSOLVED',
      outcome: 'GAVE_UP',
      correctLevel: null,
      correctListenSeconds: null,
      levels: ['WRONG', 'WRONG', 'WRONG', 'WRONG', 'WRONG'],
    }
    expect(dailyShareText(result, tDaily(), URL).split('\n')[1]).toBe('🟥🟥🟥🟥🟥 Chưa đoán ra')
  })

  it('takes the note from the language of the interface and keeps the date the same', async () => {
    await i18n.changeLanguage('en')
    const text = dailyShareText(solved, tDaily(), URL)
    expect(text.split('\n')).toEqual([
      'Meloo · 29/10/2026',
      '🟥🟩⬜⬜⬜ Right at 5s',
      'https://meloo.example/daily',
    ])
  })

  it('never carries the song title or the artist', () => {
    const text = dailyShareText(solved, tDaily(), URL)
    expect(text).not.toContain(solved.answer.title)
    expect(text).not.toContain(solved.answer.artistName)
  })
})

describe('shareDaily', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
  })

  it('uses the system share sheet where there is one', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { share })
    await expect(shareDaily({ title: 'T', text: 'body' })).resolves.toBe('shared')
    expect(share).toHaveBeenCalledWith({ title: 'T', text: 'body' })
  })

  it('treats closing the sheet as a cancel, not as a failure', async () => {
    const share = vi.fn().mockRejectedValue(new DOMException('closed', 'AbortError'))
    vi.stubGlobal('navigator', { share })
    await expect(shareDaily({ title: 'T', text: 'body' })).resolves.toBe('cancelled')
  })

  it('copies to the clipboard when there is no share sheet', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await expect(shareDaily({ title: 'T', text: 'body' })).resolves.toBe('copied')
    expect(writeText).toHaveBeenCalledWith('body')
  })

  it('falls back to the clipboard when the share sheet fails, and reports a failed copy', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    vi.stubGlobal('navigator', {
      share: vi.fn().mockRejectedValue(new Error('x')),
      clipboard: { writeText },
    })
    await expect(shareDaily({ title: 'T', text: 'body' })).resolves.toBe('failed')
    expect(writeText).toHaveBeenCalledWith('body')
  })
})
