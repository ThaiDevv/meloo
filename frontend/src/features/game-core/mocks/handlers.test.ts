import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { gameCoreHandlers } from './handlers'
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

    expect((await resultOf(1)).answer.coverUrl).toMatch(/^data:image/)
    expect('coverUrl' in (await resultOf(2)).answer).toBe(false)
    expect((await resultOf(3)).answer.coverUrl).toBe('')
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
