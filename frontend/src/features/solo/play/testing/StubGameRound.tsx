import { useState } from 'react'
import type { GameRoundProps } from '@/features/game-core'

/**
 * Stands in for game-core's GameRound in page tests: no audio, just the props it was handed and
 * one button per action, so a test can play a match through the real API layer and mock server.
 */
export function StubGameRound(props: GameRoundProps) {
  const [trackId, setTrackId] = useState('')
  const { round, result } = props
  return (
    <div data-testid="game-round">
      <p data-testid="round">
        round {round.roundNo}/{round.roundCount} level {round.level} total {props.totalScore} combo{' '}
        {props.combo?.streak}
      </p>
      {result ? (
        <>
          <p data-testid="result">
            {result.outcome} {result.roundScore} {result.answer.title}
          </p>
          <button type="button" disabled={props.busy} onClick={props.onContinue}>
            {props.matchFinished ? 'stub-finish' : 'stub-next'}
          </button>
        </>
      ) : (
        <>
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
          <p data-testid="suggestions">{props.suggestions?.map((s) => s.title).join('|')}</p>
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
        </>
      )}
    </div>
  )
}
