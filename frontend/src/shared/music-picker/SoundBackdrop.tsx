import { Volume2, VolumeX } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import './visuals.css'

const BAR_COUNT = 64
/** A made-up skyline, the same on every render: two waves multiplied. */
const SKYLINE = Array.from({ length: BAR_COUNT }, (_, i) =>
  Math.round((0.25 + 0.75 * Math.abs(Math.sin(i * 0.55) * Math.cos(i * 0.17 + 0.8))) * 100),
)
/** C major pentatonic over two octaves: whatever is pressed, it sounds fine together. */
const NOTES = [261.63, 293.66, 329.63, 392, 440, 523.25, 587.33, 659.25, 783.99, 880]
const MAX_PINGS = 8
/** A little longer than the 900 ms the ring takes to fade. */
const PING_LIFETIME_MS = 1100

interface Ping {
  id: number
  x: number
  y: number
}

function prefersReducedMotion(): boolean {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/** A short soft note: a sine that is struck and dies away. Quiet on purpose. */
function playNote(context: AudioContext, frequency: number): void {
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  const now = context.currentTime
  oscillator.type = 'sine'
  oscillator.frequency.value = frequency
  gain.gain.setValueAtTime(0.0001, now)
  gain.gain.exponentialRampToValueAtTime(0.05, now + 0.01)
  gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.55)
  oscillator.connect(gain)
  gain.connect(context.destination)
  oscillator.start(now)
  oscillator.stop(now + 0.6)
}

/**
 * The page's backdrop. A skyline of equalizer bars moves quietly along the bottom of the window,
 * and every press leaves a ring, like a sound spreading out. With the sound button on, a press
 * also plays one soft note, higher towards the right. The sound is off until the player turns it on.
 */
export function SoundBackdrop() {
  const { t } = useTranslation('solo')
  const [soundOn, setSoundOn] = useState(false)
  const [pings, setPings] = useState<Ping[]>([])
  const context = useRef<AudioContext | null>(null)
  const nextId = useRef(0)
  const timers = useRef(new Set<number>())

  useEffect(() => {
    const pending = timers.current
    return () => {
      pending.forEach((timer) => {
        window.clearTimeout(timer)
      })
    }
  }, [])

  useEffect(() => {
    const onPress = (event: PointerEvent) => {
      if (soundOn && typeof window.AudioContext === 'function') {
        context.current ??= new window.AudioContext()
        void context.current.resume()
        const column = Math.min(
          NOTES.length - 1,
          Math.floor((event.clientX / window.innerWidth) * NOTES.length),
        )
        playNote(context.current, NOTES[column] ?? NOTES[0] ?? 440)
      }
      if (prefersReducedMotion()) return
      nextId.current += 1
      const ping = { id: nextId.current, x: event.clientX, y: event.clientY }
      setPings((list) => [...list.slice(-(MAX_PINGS - 1)), ping])
      const timer = window.setTimeout(() => {
        timers.current.delete(timer)
        setPings((list) => list.filter((item) => item.id !== ping.id))
      }, PING_LIFETIME_MS)
      timers.current.add(timer)
    }
    window.addEventListener('pointerdown', onPress)
    return () => {
      window.removeEventListener('pointerdown', onPress)
    }
  }, [soundOn])

  return (
    <>
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-x-0 bottom-0 -z-10 flex h-[28vh] items-end gap-[3px] text-foreground/[0.055]"
      >
        {SKYLINE.map((height, index) => (
          <i
            key={index}
            className="mv-skyline flex-1 rounded-t-full bg-current"
            style={{ height: `${String(height)}%`, animationDelay: `${String(index * -70)}ms` }}
          />
        ))}
      </div>
      <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-20 overflow-hidden">
        {pings.map((ping) => (
          <span
            key={ping.id}
            data-testid="ping"
            className="mv-ping absolute size-40 rounded-full border-2 border-primary"
            style={{ left: ping.x, top: ping.y }}
          />
        ))}
      </div>
      <Button
        variant="outline"
        size="sm"
        aria-pressed={soundOn}
        title={t('sound.hint')}
        onClick={() => {
          setSoundOn((on) => !on)
        }}
        className="fixed right-4 bottom-4 z-30 size-11 bg-surface px-0 sm:h-9 sm:w-auto sm:px-3.5"
      >
        {soundOn ? (
          <Volume2 aria-hidden="true" className="size-4" />
        ) : (
          <VolumeX aria-hidden="true" className="size-4" />
        )}
        <span className="sr-only sm:not-sr-only">{t('sound.label')}</span>
      </Button>
    </>
  )
}
