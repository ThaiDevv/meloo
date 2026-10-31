import type { components } from '@/shared/api/schema.gen'

type Schemas = components['schemas']

export type RoundState = Schemas['RoundState']
export type RoundResult = Schemas['RoundResult']
export type RoundOutcome = Schemas['RoundOutcome']
export type TrackSuggestion = Schemas['TrackSuggestion']
export type AudioClip = Schemas['AudioClip']

/** What the server said about the last guess (the fields of GuessResponse the screen needs). */
export interface GuessVerdict {
  correct: boolean
  /** BR-06: a wrong guess by the right main artist. */
  rightArtist: boolean
  /** Changes on every guess, so the same verdict twice in a row still animates. */
  nonce: number
}

/** A timed round (daily challenge, rooms). The offset to the server clock is taken from `serverTime`. */
export interface RoundTimerState {
  endsAt: string
  serverTime: string
}
