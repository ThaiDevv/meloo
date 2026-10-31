import { http, HttpResponse } from 'msw'

const SAMPLE_RATE = 8000
/** A pentatonic phrase, so every level sounds like a tune and not a test tone. */
const NOTES = [261.63, 293.66, 329.63, 392, 440, 392, 329.63, 293.66]
const NOTE_SECONDS = 0.25

const cache = new Map<number, ArrayBuffer>()

/** A mono 16-bit WAV of `seconds` of a tune. The real endpoint serves exactly the opened level's length. */
export function mockClipWav(seconds: number): ArrayBuffer {
  const cached = cache.get(seconds)
  if (cached) return cached
  const samples = Math.round(seconds * SAMPLE_RATE)
  const buffer = new ArrayBuffer(44 + samples * 2)
  const view = new DataView(buffer)
  const text = (offset: number, value: string) => {
    for (let i = 0; i < value.length; i++) view.setUint8(offset + i, value.charCodeAt(i))
  }
  text(0, 'RIFF')
  view.setUint32(4, 36 + samples * 2, true)
  text(8, 'WAVE')
  text(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, SAMPLE_RATE, true)
  view.setUint32(28, SAMPLE_RATE * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  text(36, 'data')
  view.setUint32(40, samples * 2, true)
  for (let i = 0; i < samples; i++) {
    const t = i / SAMPLE_RATE
    const note = NOTES[Math.floor(t / NOTE_SECONDS) % NOTES.length] ?? NOTES[0] ?? 440
    const inNote = (t % NOTE_SECONDS) / NOTE_SECONDS
    const envelope = Math.min(1, inNote * 20) * (1 - inNote * 0.6)
    view.setInt16(44 + i * 2, Math.sin(2 * Math.PI * note * t) * envelope * 9000, true)
  }
  cache.set(seconds, buffer)
  return buffer
}

const LENGTHS = [2, 5, 10, 20, 30]

/** `GET /api/v1/audio/{token}`: the level is read from the mock token; real tokens are opaque. */
export const gameAudioHandlers = [
  http.get('*/api/v1/audio/:token', ({ params }) => {
    const level = Number(/level-(\d)/.exec(String(params.token))?.[1] ?? 5)
    return new HttpResponse(mockClipWav(LENGTHS[level - 1] ?? 30), {
      headers: { 'Content-Type': 'audio/wav', 'Cache-Control': 'no-store' },
    })
  }),
]
