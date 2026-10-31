import { useMemo, useState } from 'react'
import { GameRound } from '../components/GameRound'
import { pointsForLevel } from '../lib/levels'
import { foldDiacritics } from '../lib/text'
import { MOCK_COVER_URL, mockRound, mockSuggestions } from '../mocks/fixtures'
import type { GuessVerdict, RoundResult } from '../types'

const ANSWER = mockSuggestions[0]
const ROUND_COUNT = 5

/**
 * A whole match played against fixtures, with no server: the first suggestion is always the right
 * song, the other two share its artist (so they earn "Right artist!"). It shows how a host wires
 * GameRound, and gives the preview something to click. Not mounted on any route yet.
 */
export function GameCoreDemo({
  timed = false,
  withCover = true,
}: {
  timed?: boolean
  withCover?: boolean
}) {
  const [roundNo, setRoundNo] = useState(1)
  const [level, setLevel] = useState<1 | 2 | 3 | 4 | 5>(1)
  const [query, setQuery] = useState('')
  const [verdict, setVerdict] = useState<GuessVerdict | null>(null)
  const [result, setResult] = useState<RoundResult | null>(null)
  const [total, setTotal] = useState(0)
  const [streak, setStreak] = useState(0)
  const [wrong, setWrong] = useState(0)
  const [deadline] = useState(() => {
    const now = Date.now()
    return { serverTime: new Date(now).toISOString(), endsAt: new Date(now + 45_000).toISOString() }
  })

  const suggestions = useMemo(() => {
    const needle = foldDiacritics(query)
    if (!needle || !ANSWER) return []
    return mockSuggestions.filter((track) =>
      foldDiacritics(`${track.title} ${track.artistName}`).includes(needle),
    )
  }, [query])

  function finish(outcome: RoundResult['outcome'], at: number) {
    if (!ANSWER) return
    const correct = outcome === 'CORRECT'
    const fastCorrect = correct && at <= 2
    const base = correct ? pointsForLevel(at) : 0
    const comboPercent = fastCorrect ? (streak >= 1 ? (streak >= 2 ? 20 : 10) : 0) : 0
    const score = Math.round(base * (1 + comboPercent / 100))
    const nextStreak = fastCorrect ? streak + 1 : 0
    setStreak(nextStreak)
    setTotal((value) => value + score)
    setResult({
      roundNo,
      outcome,
      ...(correct && { correctLevel: at }),
      baseScore: base as RoundResult['baseScore'],
      comboPercent: comboPercent,
      speedBonus: 0,
      roundScore: score,
      wrongCount: wrong,
      comboStreak: nextStreak,
      totalScore: total + score,
      answer: {
        trackId: ANSWER.id,
        title: ANSWER.title,
        artistName: ANSWER.artistName,
        provider: 'DEEZER',
        listenUrl: 'https://www.deezer.com/track/1234567890',
        ...(withCover && { coverUrl: MOCK_COVER_URL }),
      },
    })
  }

  return (
    <GameRound
      round={mockRound(level, roundNo, ROUND_COUNT)}
      totalScore={total}
      combo={{ streak }}
      timer={timed ? deadline : undefined}
      suggestions={suggestions}
      verdict={verdict}
      result={result}
      matchFinished={roundNo === ROUND_COUNT}
      onSearch={setQuery}
      onListenMore={(expected) => {
        setLevel(Math.min(5, expected) as 1 | 2 | 3 | 4 | 5)
      }}
      onGuess={(trackId) => {
        const picked = mockSuggestions.find((track) => track.id === trackId)
        if (trackId === ANSWER?.id) {
          setVerdict({ correct: true, rightArtist: false, nonce: Date.now() })
          finish('CORRECT', level)
          return
        }
        setWrong((n) => n + 1)
        setVerdict({
          correct: false,
          rightArtist: picked?.artistName === ANSWER?.artistName,
          nonce: Date.now(),
        })
        if (level === 5) finish('WRONG_OUT', level)
        else setLevel((level + 1) as 2 | 3 | 4 | 5)
      }}
      onSkip={() => {
        finish('GAVE_UP', level)
      }}
      onTimeout={() => {
        finish('TIMEOUT', level)
      }}
      onContinue={() => {
        setRoundNo((n) => (n >= ROUND_COUNT ? 1 : n + 1))
        if (roundNo >= ROUND_COUNT) {
          setTotal(0)
          setStreak(0)
        }
        setLevel(1)
        setVerdict(null)
        setResult(null)
        setWrong(0)
      }}
    />
  )
}
