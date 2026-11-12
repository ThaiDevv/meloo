import { useState } from 'react'
import type { GameRoundProps } from '@/features/game-core'

/** Stands in for game-core's GameRound in page tests: no audio, one button per action. */
export function StubGameRound(props: GameRoundProps) {
  const [trackId, setTrackId] = useState('')
  const { round } = props
  return (
    <div data-testid="game-round">
      <p data-testid="round">level {round.level}</p>
      <p data-testid="timer">{props.timer ? props.timer.endsAt : 'no timer'}</p>
      {props.verdict ? <p data-testid="verdict">{String(props.verdict.correct)}</p> : null}
      <label>
        stub-track
        <input
          value={trackId}
          onChange={(event) => {
            setTrackId(event.target.value)
            props.onSearch(event.target.value)
          }}
        />
      </label>
      <button
        type="button"
        disabled={props.busy}
        onClick={() => {
          props.onGuess(trackId)
        }}
      >
        stub-guess
      </button>
      <button
        type="button"
        disabled={props.busy || !round.canListenMore}
        onClick={() => {
          props.onListenMore(round.level + 1)
        }}
      >
        stub-listen
      </button>
      <button type="button" disabled={props.busy || !round.canSkip} onClick={props.onSkip}>
        stub-skip
      </button>
      <button type="button" onClick={props.onTimeout}>
        stub-timeout
      </button>
    </div>
  )
}
