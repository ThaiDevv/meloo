import { useCallback, useEffect, useRef, useState } from 'react'
import { setSnippetPlaying, useSnippetPlaying } from './playbackStore'
import { SnippetError, SnippetPlayer, type AudioFailure } from './snippetPlayer'

export type SnippetStatus = 'loading' | 'ready' | 'blocked' | 'error'

interface UseSnippetPlayerOptions {
  /** URL of the opened level (`RoundState.audio.url`). */
  url: string
  /** Length of the opened level in seconds (`RoundState.listenSeconds`). */
  seconds: number
  /** Start by itself when the level opens, if the browser already allows sound. */
  autoPlay?: boolean
  /** Clips needed soon (e.g. the next round), decoded ahead of time. */
  preloadUrls?: readonly string[]
  /** The token ran out: the host should fetch the round again for a fresh URL. */
  onExpired?: () => void
  /**
   * A player owned by the caller. Pass one that outlives a single round so sound stays unlocked
   * from one round to the next (Safari/iOS allow it only after a tap). The caller disposes it.
   */
  player?: SnippetPlayer
}

interface Outcome {
  url: string
  status: Exclude<SnippetStatus, 'loading'>
  failure?: AudioFailure
}

export interface SnippetPlayerApi {
  status: SnippetStatus
  failure: AudioFailure | null
  playing: boolean
  /** Plays the opened level from its start. Call from a click or tap: it also unlocks sound. */
  play: () => void
  /** Allows sound without playing, for a click that opens the next level. */
  unlock: () => void
  retry: () => void
}

export function useSnippetPlayer({
  url,
  seconds,
  autoPlay = true,
  preloadUrls,
  onExpired,
  player: injected,
}: UseSnippetPlayerOptions): SnippetPlayerApi {
  const [own] = useState(() => new SnippetPlayer())
  const player = injected ?? own
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [attempt, setAttempt] = useState(0)
  const playing = useSnippetPlaying()
  const latestPlay = useRef(0)
  const onExpiredRef = useRef(onExpired)
  const preloadKey = (preloadUrls ?? []).join('\n')

  useEffect(() => {
    onExpiredRef.current = onExpired
  })

  // `id` comes from latestPlay: any later play, level change or unmount makes it stale, and a stale
  // play never starts a clip, however long it waited for the network or for the browser to resume.
  const start = useCallback(
    async (id: number) => {
      const isCurrent = () => latestPlay.current === id
      await player.play(url, seconds, {
        isCurrent,
        onEnded: () => {
          if (isCurrent()) setSnippetPlaying(false)
        },
      })
      if (isCurrent()) setSnippetPlaying(true)
    },
    [player, url, seconds],
  )

  const fail = useCallback(
    (error: unknown) => {
      const failure = error instanceof SnippetError ? error.kind : 'network'
      if (failure === 'blocked') setOutcome({ url, status: 'blocked' })
      else {
        setOutcome({ url, status: 'error', failure })
        if (failure === 'expired') onExpiredRef.current?.()
      }
    },
    [url],
  )

  // A new level or round: stop the old clip, decode the new one, and play it if sound is already allowed.
  useEffect(() => {
    let cancelled = false
    const isCancelled = () => cancelled
    const id = ++latestPlay.current
    player.stop()
    setSnippetPlaying(false)
    const preload = preloadKey ? preloadKey.split('\n') : []
    player.keepOnly([url, ...preload])
    for (const next of preload) player.preload(next)

    player
      .load(url)
      .then(async () => {
        if (isCancelled()) return
        if (autoPlay && player.isRunning) {
          await start(id)
          if (!isCancelled()) setOutcome({ url, status: 'ready' })
        } else {
          setOutcome({ url, status: autoPlay ? 'blocked' : 'ready' })
        }
      })
      .catch((error: unknown) => {
        if (!isCancelled()) fail(error)
      })

    return () => {
      cancelled = true
    }
  }, [player, url, autoPlay, preloadKey, attempt, start, fail])

  useEffect(
    () => () => {
      // Leaving the round (next round, or the answer screen) silences its clip.
      latestPlay.current++
      if (injected) player.stop()
      else player.dispose()
      setSnippetPlaying(false)
    },
    [player, injected],
  )

  const play = useCallback(() => {
    const id = ++latestPlay.current
    // unlock() must be reached before any await: it is what the browser ties to the tap.
    const unlocking = player.unlock()
    void unlocking
      .then(async () => {
        if (latestPlay.current !== id) return
        await start(id)
        if (latestPlay.current === id) setOutcome({ url, status: 'ready' })
      })
      .catch((error: unknown) => {
        if (latestPlay.current === id) fail(error)
      })
  }, [player, start, fail, url])

  const unlock = useCallback(() => {
    void player.unlock()
  }, [player])

  const retry = useCallback(() => {
    if (outcome?.failure === 'expired' && onExpiredRef.current) onExpiredRef.current()
    else setAttempt((n) => n + 1)
  }, [outcome])

  const current = outcome?.url === url ? outcome : null
  return {
    status: current?.status ?? 'loading',
    failure: current?.failure ?? null,
    playing,
    play,
    unlock,
    retry,
  }
}
