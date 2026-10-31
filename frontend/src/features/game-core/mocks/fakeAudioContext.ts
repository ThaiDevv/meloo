/** A stand-in for AudioContext in jsdom, which has none. Records what the player asked of it. */
export class FakeSource {
  buffer: { duration: number } | null = null
  startArgs: number[] | null = null
  stopped = false
  onended: (() => void) | null = null

  connect(): void {}

  start(...args: number[]): void {
    this.startArgs = args
  }

  stop(): void {
    this.stopped = true
    queueMicrotask(() => this.onended?.())
  }

  /** The clip reaching its end by itself. */
  finish(): void {
    this.onended?.()
  }
}

export class FakeAudioContext {
  static instances: FakeAudioContext[] = []
  /** A browser that blocks sound until a gesture: `resume()` only works once this is true. */
  static allowSound = true
  static clipSeconds = 30

  state: 'running' | 'suspended' | 'closed' = FakeAudioContext.allowSound ? 'running' : 'suspended'
  currentTime = 0
  readonly destination = {}
  readonly sources: FakeSource[] = []
  decodeCalls = 0

  constructor() {
    FakeAudioContext.instances.push(this)
  }

  resume(): Promise<void> {
    if (FakeAudioContext.allowSound) this.state = 'running'
    return Promise.resolve()
  }

  close(): Promise<void> {
    this.state = 'closed'
    return Promise.resolve()
  }

  decodeAudioData(): Promise<{ duration: number }> {
    this.decodeCalls++
    return Promise.resolve({ duration: FakeAudioContext.clipSeconds })
  }

  createBufferSource(): FakeSource {
    const source = new FakeSource()
    this.sources.push(source)
    return source
  }

  static reset(): void {
    FakeAudioContext.instances = []
    FakeAudioContext.allowSound = true
    FakeAudioContext.clipSeconds = 30
  }
}
