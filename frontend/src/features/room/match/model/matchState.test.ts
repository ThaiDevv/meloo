import { describe, expect, it } from 'vitest'
import type { RoomPlayerSummary, ServerRealtimeMessage } from '@/shared/realtime/messages'
import { GUEST_ID, HOST_ID, host, player, room } from '@/shared/realtime/testing/fixtures'
import { INITIAL_MATCH, matchReducer, rosterOf, type MatchState } from './matchState'
import { standingsOf } from './standings'

const T = '2026-11-05T10:00:00Z'
const answer = {
  trackId: 't1',
  trackTitle: 'Cắt Đôi Nỗi Sầu',
  artistName: 'Tăng Duy Tân',
  albumTitle: null,
  coverUrl: null,
  previewFullUrl: 'https://www.deezer.com/track/1',
}

function apply(state: MatchState, ...messages: object[]): MatchState {
  return messages.reduce<MatchState>(
    (acc, message) =>
      matchReducer(acc, { type: 'message', message: message as ServerRealtimeMessage }),
    state,
  )
}

const withRoster = (players: RoomPlayerSummary[]): MatchState => ({
  ...INITIAL_MATCH,
  roster: rosterOf(players, {}),
})

describe('round flow', () => {
  it('opens a round from prepare, the private clip and the start event, whatever the order', () => {
    const state = apply(
      INITIAL_MATCH,
      {
        type: 'ROUND_AUDIO_CLIP',
        roundNo: 1,
        level: 2,
        audioClip: { url: '/api/v1/audio/x', level: 2, expiresAt: T },
        serverTime: T,
      },
      { type: 'ROUND_PREPARE', roundNo: 1, totalRounds: 10, timeoutMs: 5000, serverTime: T },
      { type: 'ROUND_START', roundNo: 1, durationMs: 45000, serverTime: T },
    )
    expect(state.phase).toBe('playing')
    expect(state.clips[1]?.url).toBe('/api/v1/audio/x')
    expect(state.timer).toEqual({ serverTime: T, endsAt: '2026-11-05T10:00:45.000Z' })
  })

  it('keeps the clip of a wrong guess and marks a right one', () => {
    const wrong = apply(INITIAL_MATCH, {
      type: 'GUESS_RESULT_PRIVATE',
      roundNo: 1,
      result: 'WRONG',
      isCorrect: false,
      isCorrectArtist: true,
      scoreDetail: null,
      nextLevel: 2,
      nextAudioClip: { url: '/api/v1/audio/next', level: 5, expiresAt: T },
      serverTime: T,
    })
    expect(wrong.clips[1]).toMatchObject({ url: '/api/v1/audio/next', level: 5 })
    expect(wrong.verdict).toEqual({ correct: false, rightArtist: true, nonce: 1 })
    expect(wrong.hasGuessed).toBe(false)

    const right = apply(wrong, {
      type: 'GUESS_RESULT_PRIVATE',
      roundNo: 1,
      result: 'CORRECT',
      isCorrect: true,
      isCorrectArtist: true,
      scoreDetail: { baseScore: 800, comboPercent: 10, speedBonus: 133, roundScore: 1013 },
      nextLevel: null,
      nextAudioClip: null,
      serverTime: T,
    })
    expect(right.hasGuessed).toBe(true)
    expect(right.myScore?.roundScore).toBe(1013)
    expect(right.verdict?.nonce).toBe(2)
  })

  it('a new round forgets the last one', () => {
    const state = apply(
      INITIAL_MATCH,
      {
        type: 'PLAYER_GUESSED',
        userId: HOST_ID,
        nickname: 'ThaiPO',
        level: 5,
        isCorrect: true,
        roundScore: 500,
        totalScore: 500,
        serverTime: T,
      },
      { type: 'PLAYER_GAVE_UP', userId: GUEST_ID, roundNo: 1, serverTime: T },
      { type: 'ROUND_PREPARE', roundNo: 2, totalRounds: 10, timeoutMs: 5000, serverTime: T },
    )
    expect(state.guessed).toEqual([])
    expect(state.gaveUp).toEqual([])
    expect(state.scores[HOST_ID]?.totalScore).toBe(500)
  })

  it('reveals the answer and the scores of the round only on ROUND_END', () => {
    const before = apply(INITIAL_MATCH, {
      type: 'ROUND_START',
      roundNo: 1,
      durationMs: 45000,
      serverTime: T,
    })
    expect(JSON.stringify(before)).not.toContain('Cắt Đôi Nỗi Sầu')
    const after = apply(before, {
      type: 'ROUND_END',
      roundNo: 1,
      answer,
      results: [],
      nextRoundAt: null,
      serverTime: T,
    })
    expect(after.phase).toBe('reveal')
    expect(after.reveal?.answer.trackTitle).toBe('Cắt Đôi Nỗi Sầu')
  })
})

describe('resync', () => {
  const snapshot = (overrides: object = {}) => ({
    type: 'RESYNC_STATE',
    room: room({ status: 'PLAYING', currentRound: 2 }),
    roomPhase: 'ROUND_IN_PROGRESS',
    currentRound: {
      roundNo: 2,
      totalRounds: 10,
      currentLevel: 2,
      audioClip: { url: '/api/v1/audio/r2', level: 2, expiresAt: T },
      remainingMs: 35000,
      hasGuessed: false,
    },
    scores: [{ userId: HOST_ID, nickname: 'ThaiPO', totalScore: 1013, comboStreak: 2, rank: 1 }],
    lastRoundAnswer: null,
    lastRoundResults: null,
    serverTime: T,
    ...overrides,
  })

  it('rebuilds the round, the clock from the remaining time and the scores', () => {
    const state = apply(INITIAL_MATCH, snapshot())
    expect(state.phase).toBe('playing')
    expect(state.roundNo).toBe(2)
    expect(state.clips[2]?.url).toBe('/api/v1/audio/r2')
    expect(state.timer?.endsAt).toBe('2026-11-05T10:00:35.000Z')
    expect(state.scores[HOST_ID]).toEqual({ totalScore: 1013, comboStreak: 2 })
  })

  it('keeps what happened this round when the snapshot is of the same round', () => {
    const guessed = apply(apply(INITIAL_MATCH, snapshot()), {
      type: 'PLAYER_GUESSED',
      userId: HOST_ID,
      nickname: 'ThaiPO',
      level: 5,
      isCorrect: true,
      roundScore: 500,
      totalScore: 1513,
      serverTime: T,
    })
    expect(apply(guessed, snapshot()).guessed).toHaveLength(1)
  })

  it('shows the last answer when the snapshot is taken during the reveal', () => {
    const state = apply(
      INITIAL_MATCH,
      snapshot({
        roomPhase: 'ROUND_REVEAL',
        currentRound: null,
        lastRoundAnswer: answer,
        lastRoundResults: [],
      }),
    )
    expect(state.phase).toBe('reveal')
    expect(state.reveal?.answer.artistName).toBe('Tăng Duy Tân')
  })
})

describe('standings', () => {
  it('orders by score, shares a place on a tie and marks who left or lost the connection', () => {
    const players = [
      host({ score: 0 }),
      player({ isConnected: false }),
      player({ userId: 'c', nickname: 'Chị Ba' }),
    ]
    let state = withRoster(players)
    state = apply(state, {
      type: 'SCOREBOARD_UPDATE',
      scores: [
        { userId: HOST_ID, nickname: 'ThaiPO', totalScore: 900, comboStreak: 1, rank: 1 },
        { userId: GUEST_ID, nickname: 'PlayerTwo', totalScore: 900, comboStreak: 0, rank: 1 },
        { userId: 'c', nickname: 'Chị Ba', totalScore: 100, comboStreak: 0, rank: 3 },
      ],
      serverTime: T,
    })
    // Chị Ba left: she is no longer in the room but keeps her place on the table.
    const table = standingsOf(state, players.slice(0, 2), GUEST_ID)
    expect(table.map((row) => [row.nickname, row.rank])).toEqual([
      ['ThaiPO', 1],
      ['PlayerTwo', 1],
      ['Chị Ba', 3],
    ])
    expect(table[1]).toMatchObject({ self: true, connected: false, left: false })
    expect(table[2]).toMatchObject({ left: true })
  })
})
