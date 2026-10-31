import type { RoundResult, RoundState, TrackSuggestion } from '../types'

export const MOCK_MATCH_ID = '0f6e1b92-5a3c-4d77-8e21-9b4a7c3d5e60'

const LENGTHS = [2, 5, 10, 20, 30] as const

/** The round as the server sends it at a given level. Audio URLs are opaque, like the real ones. */
export function mockRound(
  level: 1 | 2 | 3 | 4 | 5,
  roundNo = 3,
  roundCount: 5 | 10 | 15 = 10,
): RoundState {
  return {
    matchId: MOCK_MATCH_ID,
    roundNo,
    roundCount,
    level,
    listenSeconds: LENGTHS[level - 1] ?? 30,
    canListenMore: level < 5,
    canSkip: level === 5,
    audio: {
      url: `/api/v1/audio/mock-level-${String(level)}-round-${String(roundNo)}-Hq3TnV8wYb5RsLd2`,
      expiresAt: '2026-10-23T13:12:00Z',
    },
    serverTime: '2026-10-23T13:10:05Z',
  }
}

/** The cover URL of `contracts/examples/game/get-round-result.200.json`, so mock data stays inside the contract. */
export const MOCK_COVER_URL =
  'https://e-cdns-images.dzcdn.net/images/cover/d41d8cd98f00b204e9800998ecf8427e/250x250-000000-80-0-0.jpg'

/** What the mock server answers at `MOCK_COVER_URL`: a drawn square cover, so the preview needs no network. */
export const MOCK_COVER_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400"><rect width="400" height="400" fill="#202022"/><circle cx="200" cy="200" r="150" fill="#2a2a2c"/><circle cx="200" cy="200" r="104" fill="none" stroke="#b9f24b" stroke-width="10"/><circle cx="200" cy="200" r="58" fill="none" stroke="#b9f24b" stroke-opacity=".5" stroke-width="10"/><circle cx="200" cy="200" r="14" fill="#f5f5f4"/></svg>'

const ANSWER = {
  trackId: '3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21',
  title: 'Chúng Ta Của Hiện Tại',
  artistName: 'Sơn Tùng M-TP',
  provider: 'DEEZER' as const,
  listenUrl: 'https://www.deezer.com/track/1234567890',
}

const CORRECT_BASE: RoundResult = {
  roundNo: 3,
  outcome: 'CORRECT',
  correctLevel: 2,
  baseScore: 800,
  comboPercent: 10,
  speedBonus: 0,
  roundScore: 880,
  wrongCount: 1,
  comboStreak: 2,
  totalScore: 2480,
  answer: ANSWER,
}

/** Answer with a cover. */
export const mockResultWithCover: RoundResult = {
  ...CORRECT_BASE,
  answer: { ...ANSWER, coverUrl: MOCK_COVER_URL },
}

/** Answer without the optional `coverUrl` (CT-01c). */
export const mockResultNoCover: RoundResult = { ...CORRECT_BASE }

/** The contract also allows an empty string, read as "no cover". */
export const mockResultEmptyCover: RoundResult = {
  ...CORRECT_BASE,
  answer: { ...ANSWER, coverUrl: '' },
}

export const mockResultWrongOut: RoundResult = {
  roundNo: 3,
  outcome: 'WRONG_OUT',
  baseScore: 0,
  comboPercent: 0,
  speedBonus: 0,
  roundScore: 0,
  wrongCount: 3,
  comboStreak: 0,
  totalScore: 1600,
  answer: { ...ANSWER, coverUrl: MOCK_COVER_URL },
}

export const mockSuggestions: TrackSuggestion[] = [
  {
    id: '3a9f6c10-2b7d-4e58-91c3-6d0a8f4b7e21',
    title: 'Chúng Ta Của Hiện Tại',
    artistName: 'Sơn Tùng M-TP',
  },
  {
    id: '5b1d7e22-3c8e-4f69-82d4-7e1b9a5c8f32',
    title: 'Chúng Ta Của Tương Lai',
    artistName: 'Sơn Tùng M-TP',
  },
  {
    id: '7c2e8f33-4d9f-4a70-93e5-8f2cab6d9a43',
    title: 'Chạy Ngay Đi',
    artistName: 'Sơn Tùng M-TP',
  },
]
