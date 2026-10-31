import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { SnippetPlayer } from '../audio/snippetPlayer'
import { FakeAudioContext } from '../mocks/fakeAudioContext'
import { ClipStrip } from './ClipStrip'

function newPlayer() {
  return new SnippetPlayer({
    createContext: () => new FakeAudioContext() as unknown as AudioContext,
    fetchImpl: (() => Promise.resolve(new Response(new ArrayBuffer(8)))) as unknown as typeof fetch,
    baseUrl: 'https://api.test',
  })
}

/** Bars of the unlocked part are drawn in the accent; the locked rest stays faint. */
function unlockedBars(container: HTMLElement) {
  return container.querySelectorAll('svg > rect.fill-primary')
}

beforeEach(() => {
  FakeAudioContext.reset()
})

describe('ClipStrip', () => {
  it('draws the unlocked share to scale: 10 s is a third of the 30 s strip', () => {
    const { container } = render(
      <ClipStrip
        level={3}
        listenSeconds={10}
        url="/a"
        decoded={false}
        playing={false}
        player={newPlayer()}
      />,
    )
    expect(unlockedBars(container)).toHaveLength(40)
    expect(container.querySelectorAll('svg > rect')).toHaveLength(120)
  })

  it('draws the real waveform once the clip is decoded', async () => {
    FakeAudioContext.clipSeconds = 10
    const player = newPlayer()
    await player.load('/a')
    const { container } = render(
      <ClipStrip level={3} listenSeconds={10} url="/a" decoded playing={false} player={player} />,
    )
    // The fake clip is loud in its first half and quiet in its second.
    await waitFor(() => {
      expect(unlockedBars(container)[0]?.getAttribute('height')).toBe('36')
    })
    expect(Number(unlockedBars(container)[39]?.getAttribute('height'))).toBeCloseTo(9)
  })

  it('names every level with its points and state for screen readers', () => {
    render(
      <ClipStrip
        level={2}
        listenSeconds={5}
        url="/a"
        decoded={false}
        playing={false}
        player={newPlayer()}
      />,
    )
    expect(screen.getByRole('list', { name: 'Các mức nghe' })).toBeInTheDocument()
    expect(
      screen.getByRole('listitem', { name: 'Mức 1: 2s, 1.000 điểm, đã mở' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('listitem', { name: 'Mức 2: 5s, 800 điểm, đang mở' })).toHaveAttribute(
      'aria-current',
      'step',
    )
    expect(
      screen.getByRole('listitem', { name: 'Mức 5: 30s, 200 điểm, chưa mở' }),
    ).toBeInTheDocument()
  })
})
