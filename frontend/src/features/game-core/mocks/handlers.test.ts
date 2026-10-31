import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { gameCoreHandlers } from './handlers'
import { MOCK_COVER_URL } from './fixtures'
import type { RoundResult } from '../types'

async function resultOf(roundNo: number): Promise<RoundResult> {
  const response = await fetch(
    `http://localhost/api/v1/solo/matches/m1/rounds/${String(roundNo)}/result`,
  )
  return (await response.json()) as RoundResult
}

describe('game-core mock server', () => {
  it('answers with a cover, without coverUrl, and with an empty coverUrl (AC 5)', async () => {
    server.use(...gameCoreHandlers)

    expect((await resultOf(1)).answer.coverUrl).toMatch(/^https:\/\//)
    expect('coverUrl' in (await resultOf(2)).answer).toBe(false)
    expect((await resultOf(3)).answer.coverUrl).toBe('')
  })

  it('keeps every cover inside the contract: https or the empty string', async () => {
    server.use(...gameCoreHandlers)
    for (const roundNo of [1, 2, 3]) {
      const { coverUrl } = (await resultOf(roundNo)).answer
      expect(coverUrl === undefined || /^(|https?:\/\/.+)$/.test(coverUrl)).toBe(true)
    }
  })

  it('draws the cover where the mock URL points', async () => {
    server.use(...gameCoreHandlers)
    const response = await fetch(MOCK_COVER_URL)
    expect(response.headers.get('Content-Type')).toBe('image/svg+xml')
    expect(await response.text()).toContain('<svg')
  })

  it('serves a decodable clip of the length of the opened level', async () => {
    server.use(...gameCoreHandlers)
    const bytes = await (
      await fetch('http://localhost/api/v1/audio/mock-level-2-abcdefghijklmnop')
    ).arrayBuffer()
    // 8 kHz, 16-bit mono, 5 s, plus the 44-byte WAV header.
    expect(bytes.byteLength).toBe(44 + 5 * 8000 * 2)
  })
})
