import { describe, expect, it } from 'vitest'
import correctExample from '../../../../../../contracts/examples/game/submit-guess.200.correct.json'
import lastRoundExample from '../../../../../../contracts/examples/game/submit-guess.200.last-round.json'
import wrongExample from '../../../../../../contracts/examples/game/submit-guess.200.wrong.json'
import matchExample from '../../../../../../contracts/examples/game/create-solo-match.201.json'
import resultExample from '../../../../../../contracts/examples/game/get-match-result.200.json'
import type { GuessResponse, MatchResult, SoloMatch } from '../api/soloMatchApi'
import { initialPlayState, longestCombo, playReducer } from './matchState'

const match = matchExample as SoloMatch
const start = initialPlayState(match)

describe('playReducer', () => {
  it('starts from the round, score and streak the server sent', () => {
    expect(start.round).toEqual(match.currentRound)
    expect(start.totalScore).toBe(0)
    expect(start.comboStreak).toBe(0)
    expect(start.result).toBeNull()
  })

  it('keeps playing on a wrong guess, with the next level the server opened', () => {
    const response = wrongExample as GuessResponse
    const next = playReducer(start, { type: 'guessed', response, nonce: 1 })
    expect(next.round).toEqual(response.round)
    expect(next.verdict).toEqual({ correct: false, rightArtist: response.rightArtist, nonce: 1 })
    expect(next.result).toBeNull()
  })

  it('copies score and combo from the round result, never adding them up itself', () => {
    const response = correctExample as GuessResponse
    const next = playReducer(
      { ...start, totalScore: 99_999, comboStreak: 7 },
      { type: 'guessed', response, nonce: 2 },
    )
    expect(next.result).toEqual(response.end?.result)
    expect(next.totalScore).toBe(800)
    expect(next.comboStreak).toBe(1)
    expect(next.nextRound).toEqual(response.end?.nextRound)
    expect(next.matchFinished).toBe(false)
  })

  it('moves to the round the server already opened on continue', () => {
    const ended = playReducer(start, {
      type: 'guessed',
      response: correctExample as GuessResponse,
      nonce: 3,
    })
    const next = playReducer(ended, { type: 'continued' })
    expect(next.round.roundNo).toBe(2)
    expect(next.result).toBeNull()
    expect(next.verdict).toBeNull()
    expect(next.nextRound).toBeNull()
  })

  it('marks the match finished after the last round and does not move on', () => {
    const ended = playReducer(start, {
      type: 'guessed',
      response: lastRoundExample as GuessResponse,
      nonce: 4,
    })
    expect(ended.matchFinished).toBe(true)
    expect(ended.totalScore).toBe(1900)
    expect(playReducer(ended, { type: 'continued' })).toBe(ended)
  })

  it('takes the server round on a resync and drops what the screen showed', () => {
    const shown = playReducer(start, {
      type: 'guessed',
      response: wrongExample as GuessResponse,
      nonce: 5,
    })
    const fresh = { ...match.currentRound, level: 4 }
    const next = playReducer(shown, { type: 'resynced', round: fresh })
    expect(next.round).toEqual(fresh)
    expect(next.verdict).toBeNull()
  })
})

describe('longestCombo', () => {
  it('is the highest streak the server reported', () => {
    expect(longestCombo(resultExample as MatchResult)).toBe(2)
  })

  it('is 0 with no finished round', () => {
    expect(longestCombo({ ...(resultExample as MatchResult), rounds: [] })).toBe(0)
  })
})
