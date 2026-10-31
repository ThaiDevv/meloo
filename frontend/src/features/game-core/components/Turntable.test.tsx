import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SnippetPlayer } from '../audio/snippetPlayer'
import { Turntable } from './Turntable'

function arm(container: HTMLElement) {
  return container.querySelector<SVGGElement>('.gc-arm')
}

describe('Turntable', () => {
  it('drops the arm on the record while the clip plays and lifts it after', () => {
    const player = new SnippetPlayer()
    const view = render(<Turntable level={2} listenSeconds={5} playing player={player} />)
    expect(arm(view.container)?.style.transform).toBe('rotate(24deg)')

    view.rerender(<Turntable level={2} listenSeconds={5} playing={false} player={player} />)
    expect(arm(view.container)?.style.transform).toBe('rotate(3deg)')
  })

  it('lights the grooves of the opened levels and prints the opened length', () => {
    const { container } = render(
      <Turntable level={3} listenSeconds={10} playing={false} player={new SnippetPlayer()} />,
    )
    const states = [...container.querySelectorAll('.gc-ring')].map((ring) =>
      ring.getAttribute('data-state'),
    )
    expect(states).toEqual(['opened', 'opened', 'current', 'locked', 'locked'])
    expect(container).toHaveTextContent('10s')
  })
})
