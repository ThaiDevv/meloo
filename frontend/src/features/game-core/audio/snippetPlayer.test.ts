import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FakeAudioContext } from '../mocks/fakeAudioContext'
import { SnippetError, SnippetPlayer } from './snippetPlayer'

function makePlayer(responder: () => Response = () => new Response(new ArrayBuffer(16))) {
  const fetchImpl = vi.fn(() => Promise.resolve(responder()))
  const player = new SnippetPlayer({
    createContext: () => new FakeAudioContext() as unknown as AudioContext,
    fetchImpl: fetchImpl as unknown as typeof fetch,
    baseUrl: 'https://api.test',
  })
  return { player, fetchImpl }
}

const context = () => FakeAudioContext.instances[0] as FakeAudioContext

beforeEach(() => {
  FakeAudioContext.reset()
})
afterEach(() => {
  FakeAudioContext.reset()
})

describe('SnippetPlayer', () => {
  it('plays exactly the opened level from its start, on the audio clock (AC 1)', async () => {
    const { player } = makePlayer()
    await player.play('/api/v1/audio/abc', 5)

    const [source] = context().sources
    expect(source?.startArgs).toEqual([0, 0, 5])
  })

  it('never plays longer than the clip it has', async () => {
    FakeAudioContext.clipSeconds = 2
    const { player } = makePlayer()
    await player.play('/api/v1/audio/abc', 5)
    expect(context().sources[0]?.startArgs?.[2]).toBe(2)
  })

  it('listens again from the decoded buffer without another request (BR-03)', async () => {
    const { player, fetchImpl } = makePlayer()
    await player.play('/api/v1/audio/abc', 5)
    await player.play('/api/v1/audio/abc', 5)
    await player.play('/api/v1/audio/abc', 5)

    expect(fetchImpl).toHaveBeenCalledTimes(1)
    expect(context().decodeCalls).toBe(1)
    expect(context().sources).toHaveLength(3)
    // A source starts only once, so each replay gets a new one and the old one is stopped.
    expect(context().sources[0]?.stopped).toBe(true)
    expect(context().sources[1]?.stopped).toBe(true)
    expect(context().sources[2]?.stopped).toBe(false)
  })

  it('resolves a relative clip URL against the API origin', async () => {
    const { player, fetchImpl } = makePlayer()
    await player.load('/api/v1/audio/abc')
    expect(fetchImpl).toHaveBeenCalledWith('https://api.test/api/v1/audio/abc')
  })

  it('passes the elapsed audio-clock time to onEnded (fake clock: not a measurement of real latency)', async () => {
    const { player } = makePlayer()
    const onEnded = vi.fn()
    await player.play('/api/v1/audio/abc', 5, { onEnded })
    context().currentTime = 5.012
    context().sources[0]?.finish()

    expect(onEnded).toHaveBeenCalledOnce()
    const elapsed = onEnded.mock.calls[0]?.[0] as number
    expect(Math.abs(elapsed - 5)).toBeLessThan(0.05)
  })

  it('says "blocked" when the browser has not allowed sound', async () => {
    FakeAudioContext.allowSound = false
    const { player } = makePlayer()
    await expect(player.play('/api/v1/audio/abc', 2)).rejects.toMatchObject({ kind: 'blocked' })
    expect(context().sources).toHaveLength(0)
  })

  it('unlock resumes a suspended context', async () => {
    FakeAudioContext.allowSound = false
    const { player } = makePlayer()
    expect(await player.unlock()).toBe(false)
    FakeAudioContext.allowSound = true
    expect(await player.unlock()).toBe(true)
    expect(player.isRunning).toBe(true)
  })

  it.each([
    [401, 'expired'],
    [403, 'expired'],
    [500, 'network'],
  ])(
    'maps HTTP %i to "%s" and forgets the failure so a retry fetches again',
    async (status, kind) => {
      let ok = false
      const { player, fetchImpl } = makePlayer(() =>
        ok ? new Response(new ArrayBuffer(8)) : new Response(null, { status }),
      )
      const error = await player.load('/api/v1/audio/abc').catch((e: unknown) => e)
      expect(error).toBeInstanceOf(SnippetError)
      expect((error as SnippetError).kind).toBe(kind)

      ok = true
      await expect(player.load('/api/v1/audio/abc')).resolves.toBeDefined()
      expect(fetchImpl).toHaveBeenCalledTimes(2)
    },
  )

  it('reports a network failure and a decode failure apart', async () => {
    const offline = new SnippetPlayer({
      createContext: () => new FakeAudioContext() as unknown as AudioContext,
      fetchImpl: (() => Promise.reject(new TypeError('offline'))) as unknown as typeof fetch,
      baseUrl: 'https://api.test',
    })
    await expect(offline.load('/a')).rejects.toMatchObject({ kind: 'network' })

    const ctx = new FakeAudioContext()
    ctx.decodeAudioData = () => Promise.reject(new Error('bad data'))
    const broken = new SnippetPlayer({
      createContext: () => ctx as unknown as AudioContext,
      fetchImpl: (() =>
        Promise.resolve(new Response(new ArrayBuffer(4)))) as unknown as typeof fetch,
      baseUrl: 'https://api.test',
    })
    await expect(broken.load('/b')).rejects.toMatchObject({ kind: 'decode' })
  })

  it('keepOnly drops clips a later round no longer needs', async () => {
    const { player, fetchImpl } = makePlayer()
    await player.load('/a')
    await player.load('/b')
    player.keepOnly(['/b'])
    await player.load('/a')
    await player.load('/b')
    expect(fetchImpl).toHaveBeenCalledTimes(3)
  })
})

describe('SnippetPlayer.play with isCurrent', () => {
  it('does not start the clip when the caller moved on while sound was resuming', async () => {
    FakeAudioContext.allowSound = false
    let release: () => void = () => undefined
    FakeAudioContext.resumeGate = new Promise<void>((resolve) => {
      release = resolve
    })
    const { player } = makePlayer()
    let current = true
    const playing = player.play('/api/v1/audio/abc', 2, { isCurrent: () => current })
    await Promise.resolve()
    current = false
    FakeAudioContext.allowSound = true
    release()
    await playing

    expect(context().sources).toHaveLength(0)
  })
})

describe('SnippetPlayer.dispose', () => {
  it('does not revive the audio context for a clip that finished loading afterwards', async () => {
    let release: (value: Response) => void = () => undefined
    const fetchImpl = vi.fn(() => new Promise<Response>((resolve) => (release = resolve)))
    const player = new SnippetPlayer({
      createContext: () => new FakeAudioContext() as unknown as AudioContext,
      fetchImpl: fetchImpl as unknown as typeof fetch,
      baseUrl: 'https://api.test',
    })
    const loading = player.play('/a', 2)
    player.dispose()
    release(new Response(new ArrayBuffer(8)))
    await loading.catch(() => undefined)

    expect(FakeAudioContext.instances).toHaveLength(0)
  })
})

describe('SnippetPlayer: what the screen draws', () => {
  it('passes every clip through one analyser on its way to the speakers', async () => {
    const { player } = makePlayer()
    await player.play('/a', 2)
    await player.play('/a', 2)

    const [analyser] = context().analysers
    expect(context().analysers).toHaveLength(1)
    expect(analyser?.connectedTo).toBe(context().destination)
    expect(context().sources.every((source) => source.connectedTo === analyser)).toBe(true)
    expect(player.analyserNode).toBe(analyser)
  })

  it('tells how far the clip has got on the audio clock, and nothing once it stops', async () => {
    FakeAudioContext.clipSeconds = 10
    const { player } = makePlayer()
    expect(player.position()).toBeNull()

    await player.play('/a', 10)
    context().currentTime = 4
    expect(player.position()).toEqual({ elapsed: 4, duration: 10 })
    context().currentTime = 99
    expect(player.position()?.elapsed).toBe(10)

    player.stop()
    expect(player.position()).toBeNull()
  })

  it('measures the waveform of the decoded clip, per second, loudest bar = 1', async () => {
    FakeAudioContext.clipSeconds = 2
    const { player } = makePlayer()
    const peaks = await player.waveform('/a', 4)

    // 2 s at 4 bars a second; the fake clip is loud (0.8) then quiet (0.2).
    expect(peaks).toHaveLength(8)
    expect(peaks.slice(0, 4)).toEqual([1, 1, 1, 1])
    expect(peaks[7]).toBeCloseTo(0.25)
  })
})
