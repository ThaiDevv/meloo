/** A stand-in for AudioContext in jsdom, which has none. Records what the player asked of it. */
export class FakeSource {
  buffer: { duration: number } | null = null
  startArgs: number[] | null = null
  stopped = false
  onended: (() => void) | null = null
  connectedTo: unknown = null

  connect(node: unknown): void {
    this.connectedTo = node
  }

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

export class FakeAnalyser {
  fftSize = 2048
  smoothingTimeConstant = 0.8
  connectedTo: unknown = null

  get frequencyBinCount(): number {
    return this.fftSize / 2
  }

  connect(node: unknown): void {
    this.connectedTo = node
  }

  getByteFrequencyData(array: Uint8Array): void {
    array.fill(128)
  }
}

/** A decoded clip: a loud first half and a quiet second half, so a waveform has a shape to check. */
export class FakeBuffer {
  readonly sampleRate = 1000
  readonly duration: number
  private readonly data: Float32Array

  constructor(duration: number) {
    this.duration = duration
    this.data = new Float32Array(Math.round(duration * this.sampleRate))
    const half = this.data.length / 2
    for (let i = 0; i < this.data.length; i++) this.data[i] = i < half ? 0.8 : 0.2
  }

  get length(): number {
    return this.data.length
  }

  getChannelData(): Float32Array {
    return this.data
  }
}

export class FakeAudioContext {
  static instances: FakeAudioContext[] = []
  /** A browser that blocks sound until a gesture: `resume()` only works once this is true. */
  static allowSound = true
  static clipSeconds = 30
  /** When set, `resume()` waits for it: a browser that takes a while to allow sound. */
  static resumeGate: Promise<void> | null = null

  state: 'running' | 'suspended' | 'closed' = FakeAudioContext.allowSound ? 'running' : 'suspended'
  currentTime = 0
  readonly destination = {}
  readonly sources: FakeSource[] = []
  readonly analysers: FakeAnalyser[] = []
  decodeCalls = 0

  constructor() {
    FakeAudioContext.instances.push(this)
  }

  async resume(): Promise<void> {
    await FakeAudioContext.resumeGate
    if (FakeAudioContext.allowSound) this.state = 'running'
  }

  close(): Promise<void> {
    this.state = 'closed'
    return Promise.resolve()
  }

  decodeAudioData(): Promise<FakeBuffer> {
    this.decodeCalls++
    return Promise.resolve(new FakeBuffer(FakeAudioContext.clipSeconds))
  }

  createBufferSource(): FakeSource {
    const source = new FakeSource()
    this.sources.push(source)
    return source
  }

  createAnalyser(): FakeAnalyser {
    const analyser = new FakeAnalyser()
    this.analysers.push(analyser)
    return analyser
  }

  static reset(): void {
    FakeAudioContext.instances = []
    FakeAudioContext.allowSound = true
    FakeAudioContext.clipSeconds = 30
    FakeAudioContext.resumeGate = null
  }
}
