import { http, HttpResponse } from 'msw'
import type { components } from '@/shared/api/schema.gen'
import reportsExample from '../../../../../contracts/examples/admin/list-admin-reports.200.json'
import wordsExample from '../../../../../contracts/examples/admin/list-admin-banned-words.200.json'
import scheduleExample from '../../../../../contracts/examples/admin/list-admin-daily-schedule.200.json'
import tracks from '../../../../../contracts/examples/admin/list-admin-tracks.200.json'
import { vietnamToday } from './dates'

type Schema = components['schemas']
let reports: Schema['AdminChatReport'][] = reportsExample.items.map((item) => ({
  ...item,
  status: 'OPEN',
}))
let words: Schema['AdminBannedWord'][] = [...wordsExample.items]
const today = vietnamToday()
const tomorrow = new Date(`${today}T00:00:00Z`)
tomorrow.setUTCDate(tomorrow.getUTCDate() + 1)
let schedule: Schema['AdminDailyScheduleItem'][] = scheduleExample.items.map((item, i) => ({
  ...item,
  provider: 'DEEZER',
  source: 'SCHEDULED',
  challengeDate: i === 0 ? today : tomorrow.toISOString().slice(0, 10),
  locked: i === 0,
}))
const invalid = () => HttpResponse.json({ code: 'VALIDATION_FAILED' }, { status: 400 })

/** Preview-only contract fixtures; state is discarded on reload, and audit remains a static fixture. */
export const adminModerationHandlers = [
  http.get('*/api/v1/admin/reports', ({ request }) => {
    const q = new URL(request.url).searchParams
    const filtered = reports.filter((item) => item.status === (q.get('status') ?? 'OPEN'))
    const page = Number(q.get('page') ?? 0),
      size = Number(q.get('size') ?? 20)
    return HttpResponse.json({
      items: filtered.slice(page * size, (page + 1) * size),
      page,
      size,
      totalItems: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
    })
  }),
  http.post('*/api/v1/admin/reports/:id/resolve', async ({ request, params }) => {
    const body: unknown = await request.json()
    const report = reports.find((item) => item.id === params.id)
    if (!report) return HttpResponse.json({ code: 'REPORT_NOT_FOUND' }, { status: 404 })
    if (
      !body ||
      typeof body !== 'object' ||
      !('status' in body) ||
      (body.status !== 'DISMISSED' && body.status !== 'ACTIONED')
    )
      return invalid()
    if (
      'lockSender' in body &&
      body.lockSender &&
      (!('lockReason' in body) || typeof body.lockReason !== 'string' || !body.lockReason.trim())
    )
      return invalid()
    const saved: Schema['AdminChatReport'] = {
      ...report,
      status: body.status,
      handledAt: new Date().toISOString(),
    }
    reports = reports.map((item) => (item.id === saved.id ? saved : item))
    return HttpResponse.json(saved)
  }),
  http.get('*/api/v1/admin/banned-words', () => HttpResponse.json({ items: words })),
  http.post('*/api/v1/admin/banned-words', async ({ request }) => {
    const body: unknown = await request.json()
    if (
      !body ||
      typeof body !== 'object' ||
      !('word' in body) ||
      typeof body.word !== 'string' ||
      !body.word.trim()
    )
      return invalid()
    const word = body.word.trim().normalize('NFC').toLowerCase()
    if (words.some((item) => item.word === word))
      return HttpResponse.json({ code: 'BANNED_WORD_ALREADY_EXISTS' }, { status: 409 })
    const saved = { id: crypto.randomUUID(), word, createdAt: new Date().toISOString() }
    words = [...words, saved]
    return HttpResponse.json(saved, { status: 201 })
  }),
  http.put('*/api/v1/admin/banned-words/:id', async ({ request, params }) => {
    const body: unknown = await request.json()
    const old = words.find((item) => item.id === params.id)
    if (!old) return HttpResponse.json({}, { status: 404 })
    if (
      !body ||
      typeof body !== 'object' ||
      !('word' in body) ||
      typeof body.word !== 'string' ||
      !body.word.trim()
    )
      return invalid()
    const word = body.word.trim().normalize('NFC').toLowerCase()
    if (words.some((item) => item.id !== old.id && item.word === word))
      return HttpResponse.json({ code: 'BANNED_WORD_ALREADY_EXISTS' }, { status: 409 })
    const saved = { ...old, word }
    words = words.map((item) => (item.id === old.id ? saved : item))
    return HttpResponse.json(saved)
  }),
  http.delete('*/api/v1/admin/banned-words/:id', ({ params }) => {
    words = words.filter((item) => item.id !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('*/api/v1/admin/daily-schedule', ({ request }) => {
    const q = new URL(request.url).searchParams
    return HttpResponse.json({
      items: schedule.filter(
        (item) =>
          (!q.get('from') || item.challengeDate >= (q.get('from') ?? '')) &&
          (!q.get('to') || item.challengeDate <= (q.get('to') ?? '')),
      ),
    })
  }),
  http.put('*/api/v1/admin/daily-schedule/:date', async ({ request, params }) => {
    const date = String(params.date)
    if (date <= vietnamToday())
      return HttpResponse.json({ code: 'DAILY_CHALLENGE_LOCKED' }, { status: 409 })
    const body: unknown = await request.json()
    if (!body || typeof body !== 'object' || !('trackId' in body)) return invalid()
    const track = tracks.items.find((item) => item.id === body.trackId && item.enabled)
    if (!track) return HttpResponse.json({ code: 'TRACK_NOT_FOUND' }, { status: 404 })
    const saved: Schema['AdminDailyScheduleItem'] = {
      challengeDate: date,
      trackId: track.id,
      title: track.title,
      artistName: track.artistName,
      provider: 'DEEZER',
      source: 'SCHEDULED',
      locked: false,
      createdAt: new Date().toISOString(),
    }
    schedule = [...schedule.filter((item) => item.challengeDate !== date), saved].sort((a, b) =>
      a.challengeDate.localeCompare(b.challengeDate),
    )
    return HttpResponse.json(saved)
  }),
]
