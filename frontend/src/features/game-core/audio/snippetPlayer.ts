import { DEFAULT_BASE_URL } from '@/shared/api/baseUrl'

export type AudioFailure = 'expired' | 'network' | 'decode' | 'blocked'

/** Why a clip could not be loaded or played. `expired` means the token ran out: ask the server for a new URL. */
export class SnippetError extends Error {
  readonly kind: AudioFailure

  constructor(kind: AudioFailure, message?: string) {
    super(message ?? kind)
    this.name = 'SnippetError'
    this.kind = kind
  }
}

export interface SnippetPlayerOptions {
  createContext?: () => AudioContext
  fetchImpl?: typeof fetch
  /** Origin the relative `audio.url` belongs to. */
  baseUrl?: string
}

export interface PlayHandlers {
  /**
   * Asked again after every wait inside `play()`. When it says false (the round moved on while the
   * clip was loading or the browser was still resuming sound) the clip is not started.
   */
  isCurrent?: () => boolean
  /** Called when the clip stops by itself or is stopped. `elapsed` is measured on the audio clock (seconds). */
  onEnded?: (elapsed: number) => void
}

/**
 * Plays opaque, short-lived clips with Web Audio (ADR-008). Each clip is fetched and decoded once;
 * replaying uses the decoded buffer, so BR-03 ("listen again, unlimited") costs no request.
 * A clip is never handed to an <audio src>, which would put the provider's URL in the page.
 */
export class SnippetPlayer {
  private context: AudioContext | null = null
  private readonly buffers = new Map<string, Promise<AudioBuffer>>()
  private source: AudioBufferSourceNode | null = null
  /** Bumped by dispose(), so work that was waiting on the network cannot revive a closed player. */
  private epoch = 0
  private readonly createContext: () => AudioContext
  private readonly fetchImpl: typeof fetch
  private readonly baseUrl: string

  constructor(options: SnippetPlayerOptions = {}) {
    this.createContext = options.createContext ?? (() => new AudioContext())
    this.fetchImpl = options.fetchImpl ?? ((...args) => fetch(...args))
    this.baseUrl = options.baseUrl ?? DEFAULT_BASE_URL
  }

  get isRunning(): boolean {
    return this.context?.state === 'running'
  }

  /**
   * Creates and resumes the AudioContext. Call it synchronously inside a click or tap handler:
   * Safari and iOS refuse to start sound otherwise. Resolves true when sound is allowed.
   */
  async unlock(): Promise<boolean> {
    const context = this.ensureContext()
    if (context.state === 'suspended') {
      try {
        await context.resume()
      } catch {
        return false
      }
    }
    return context.state === 'running'
  }

  /** Fetches and decodes a clip once; later calls get the same buffer. A failed load is forgotten so it can be retried. */
  load(url: string): Promise<AudioBuffer> {
    const cached = this.buffers.get(url)
    if (cached) return cached
    const pending = this.fetchAndDecode(url, this.epoch)
    this.buffers.set(url, pending)
    pending.catch(() => {
      if (this.buffers.get(url) === pending) this.buffers.delete(url)
    })
    return pending
  }

  /** Warms the cache for a clip that will be needed soon. Failures surface later, when the clip is played. */
  preload(url: string): void {
    this.load(url).catch(() => undefined)
  }

  /** Forgets decoded clips that are not in `keep` (a round only needs its current level). */
  keepOnly(keep: readonly string[]): void {
    for (const url of this.buffers.keys()) {
      if (!keep.includes(url)) this.buffers.delete(url)
    }
  }

  /**
   * Plays the first `seconds` of the clip from the start. The duration is given to `start()`, so the
   * clip stops on the audio clock, not on a timer. Throws SnippetError('blocked') when the browser
   * has not allowed sound yet.
   */
  async play(url: string, seconds: number, handlers: PlayHandlers = {}): Promise<void> {
    const epoch = this.epoch
    const buffer = await this.load(url)
    if (epoch !== this.epoch || handlers.isCurrent?.() === false) return
    const context = this.ensureContext()
    if (context.state === 'suspended') await context.resume().catch(() => undefined)
    if (epoch !== this.epoch || handlers.isCurrent?.() === false) return
    if (context.state !== 'running') throw new SnippetError('blocked')

    this.stop()
    const source = context.createBufferSource()
    source.buffer = buffer
    source.connect(context.destination)
    const startedAt = context.currentTime
    source.onended = () => {
      if (this.source === source) this.source = null
      handlers.onEnded?.(context.currentTime - startedAt)
    }
    this.source = source
    source.start(startedAt, 0, Math.min(seconds, buffer.duration))
  }

  /** Stops the clip that is playing, if any. Its `onEnded` still fires. */
  stop(): void {
    const source = this.source
    this.source = null
    if (!source) return
    try {
      source.stop()
    } catch {
      // Already ended.
    }
  }

  dispose(): void {
    this.epoch++
    this.stop()
    this.buffers.clear()
    const context = this.context
    this.context = null
    if (context && context.state !== 'closed') void context.close().catch(() => undefined)
  }

  private ensureContext(): AudioContext {
    this.context ??= this.createContext()
    return this.context
  }

  private async fetchAndDecode(url: string, epoch: number): Promise<AudioBuffer> {
    let response: Response
    try {
      response = await this.fetchImpl(new URL(url, this.baseUrl || undefined).toString())
    } catch {
      throw new SnippetError('network')
    }
    if (response.status === 401 || response.status === 403) throw new SnippetError('expired')
    if (!response.ok) throw new SnippetError('network', `HTTP ${String(response.status)}`)

    let bytes: ArrayBuffer
    try {
      bytes = await response.arrayBuffer()
    } catch {
      throw new SnippetError('network')
    }
    if (epoch !== this.epoch) throw new SnippetError('network', 'disposed')
    try {
      return await this.ensureContext().decodeAudioData(bytes)
    } catch {
      throw new SnippetError('decode')
    }
  }
}
