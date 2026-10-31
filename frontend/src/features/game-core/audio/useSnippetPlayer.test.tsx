import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { gameAudioHandlers } from '../mocks/audio'
import { FakeAudioContext } from '../mocks/fakeAudioContext'
import { isSnippetPlaying, useSnippetPlaying } from './playbackStore'
import { SnippetPlayer } from './snippetPlayer'
import { useSnippetPlayer } from './useSnippetPlayer'

function Harness({
  url,
  onExpired,
  autoPlay,
  player,
}: {
  url: string
  onExpired?: () => void
  autoPlay?: boolean
  player: SnippetPlayer
}) {
  const audio = useSnippetPlayer({ url, seconds: 5, onExpired, autoPlay, player })
  const voiceWouldMute = useSnippetPlaying()
  return (
    <div>
      <p data-testid="status">{audio.status}</p>
      <p data-testid="failure">{audio.failure ?? ''}</p>
      <p data-testid="playing">{String(audio.playing)}</p>
      <p data-testid="voice">{String(voiceWouldMute)}</p>
      <button onClick={audio.play}>play</button>
      <button onClick={audio.retry}>retry</button>
    </div>
  )
}

function newPlayer() {
  return new SnippetPlayer({
    createContext: () => new FakeAudioContext() as unknown as AudioContext,
  })
}

const URL_OK = '/api/v1/audio/mock-level-2-round-1-abcdefghijklmnop'
const status = () => screen.getByTestId('status').textContent

beforeEach(() => {
  FakeAudioContext.reset()
  server.use(...gameAudioHandlers)
})
afterEach(() => {
  FakeAudioContext.reset()
})

describe('useSnippetPlayer', () => {
  it('plays by itself when the browser already allows sound, and tells voice chat (BR-19)', async () => {
    render(<Harness url={URL_OK} player={newPlayer()} />)
    await waitFor(() => {
      expect(screen.getByTestId('playing')).toHaveTextContent('true')
    })
    expect(status()).toBe('ready')
    expect(screen.getByTestId('voice')).toHaveTextContent('true')
    expect(isSnippetPlaying()).toBe(true)

    act(() => {
      FakeAudioContext.instances[0]?.sources[0]?.finish()
    })
    expect(screen.getByTestId('voice')).toHaveTextContent('false')
  })

  it('waits for a tap when the browser blocks autoplay (Safari/iOS), then plays', async () => {
    FakeAudioContext.allowSound = false
    render(<Harness url={URL_OK} player={newPlayer()} />)
    await waitFor(() => {
      expect(status()).toBe('blocked')
    })
    expect(screen.getByTestId('playing')).toHaveTextContent('false')

    FakeAudioContext.allowSound = true
    await userEvent.click(screen.getByRole('button', { name: 'play' }))
    await waitFor(() => {
      expect(screen.getByTestId('playing')).toHaveTextContent('true')
    })
    expect(status()).toBe('ready')
  })

  it('does not start without autoplay but is ready to play', async () => {
    render(<Harness url={URL_OK} player={newPlayer()} autoPlay={false} />)
    await waitFor(() => {
      expect(status()).toBe('ready')
    })
    expect(screen.getByTestId('playing')).toHaveTextContent('false')
  })

  it('shows a load error and loads again on retry', async () => {
    let fail = true
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.get('*/api/v1/audio/:token', () =>
        fail ? new HttpResponse(null, { status: 500 }) : new HttpResponse(new ArrayBuffer(8)),
      ),
    )
    render(<Harness url={URL_OK} player={newPlayer()} />)
    await waitFor(() => {
      expect(status()).toBe('error')
    })
    expect(screen.getByTestId('failure')).toHaveTextContent('network')

    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'retry' }))
    await waitFor(() => {
      expect(screen.getByTestId('playing')).toHaveTextContent('true')
    })
  })

  it('asks the host for a fresh URL when the token has expired', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(http.get('*/api/v1/audio/:token', () => new HttpResponse(null, { status: 401 })))
    const onExpired = vi.fn()
    render(<Harness url={URL_OK} player={newPlayer()} onExpired={onExpired} />)
    await waitFor(() => {
      expect(screen.getByTestId('failure')).toHaveTextContent('expired')
    })
    expect(onExpired).toHaveBeenCalledOnce()

    await userEvent.click(screen.getByRole('button', { name: 'retry' }))
    expect(onExpired).toHaveBeenCalledTimes(2)
  })

  it('stops the old level when a new one opens', async () => {
    const player = newPlayer()
    const view = render(<Harness url={URL_OK} player={player} />)
    await waitFor(() => {
      expect(screen.getByTestId('playing')).toHaveTextContent('true')
    })
    view.rerender(
      <Harness url="/api/v1/audio/mock-level-3-round-1-abcdefghijklmnop" player={player} />,
    )
    await waitFor(() => {
      expect(FakeAudioContext.instances[0]?.sources).toHaveLength(2)
    })
    expect(FakeAudioContext.instances[0]?.sources[0]?.stopped).toBe(true)
  })

  it('does not start a clip that was waiting for sound when the round has ended', async () => {
    FakeAudioContext.allowSound = false
    const player = newPlayer()
    const view = render(<Harness url={URL_OK} player={player} />)
    await waitFor(() => {
      expect(status()).toBe('blocked')
    })

    // The browser takes a while to allow sound after the tap...
    let release: () => void = () => undefined
    FakeAudioContext.resumeGate = new Promise<void>((resolve) => {
      release = resolve
    })
    await userEvent.click(screen.getByRole('button', { name: 'play' }))
    // ...and the round ends (the answer screen replaces it) before it does.
    view.unmount()
    FakeAudioContext.allowSound = true
    release()
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(FakeAudioContext.instances[0]?.sources ?? []).toHaveLength(0)
    expect(isSnippetPlaying()).toBe(false)
  })

  it('lets the newest tap win when two are waiting for sound', async () => {
    FakeAudioContext.allowSound = false
    render(<Harness url={URL_OK} player={newPlayer()} />)
    await waitFor(() => {
      expect(status()).toBe('blocked')
    })
    let release: () => void = () => undefined
    FakeAudioContext.resumeGate = new Promise<void>((resolve) => {
      release = resolve
    })
    await userEvent.click(screen.getByRole('button', { name: 'play' }))
    await userEvent.click(screen.getByRole('button', { name: 'play' }))
    FakeAudioContext.allowSound = true
    release()
    await waitFor(() => {
      expect(screen.getByTestId('playing')).toHaveTextContent('true')
    })
    expect(FakeAudioContext.instances[0]?.sources).toHaveLength(1)
  })
})
