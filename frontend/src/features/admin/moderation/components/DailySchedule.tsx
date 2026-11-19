import { vietnamToday } from '../dates'
import { formText } from '../../users/utils'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { Field } from '../../shell/fields'
import { useAdminTracks, type AdminTrack } from '../../catalog/api/catalogApi'
import { ConfirmChange, PageNav, QueryState } from '../../users/components/AdminFeedback'
import { useModerationChange, useSchedule } from '../api/moderationApi'

export function DailySchedule() {
  const { t } = useTranslation('admin')
  const [range, setRange] = useState<{ from?: string; to?: string }>({})
  const [rangeDraft, setRangeDraft] = useState({ from: '', to: '' })
  const [date, setDate] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<AdminTrack | null>(null)
  const [confirm, setConfirm] = useState(false)
  const [saved, setSaved] = useState(false)
  const query = useSchedule(range)
  const tracks = useAdminTracks({ q, enabled: true, page }, q !== '')
  const mutation = useModerationChange()
  const today = vietnamToday()
  return (
    <div className="space-y-5">
      <p className="text-sm text-muted-foreground">{t('schedule.hint')}</p>
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault()
          const data = new FormData(e.currentTarget)
          setRange({
            from: formText(data, 'from') || undefined,
            to: formText(data, 'to') || undefined,
          })
        }}
      >
        <Field label={t('manage.fromDate')} htmlFor="schedule-from">
          <Input
            id="schedule-from"
            name="from"
            type="date"
            value={rangeDraft.from}
            max={rangeDraft.to || undefined}
            onChange={(e) => {
              setRangeDraft({ ...rangeDraft, from: e.target.value })
            }}
          />
        </Field>
        <Field label={t('manage.toDate')} htmlFor="schedule-to">
          <Input
            id="schedule-to"
            name="to"
            type="date"
            value={rangeDraft.to}
            min={rangeDraft.from || undefined}
            onChange={(e) => {
              setRangeDraft({ ...rangeDraft, to: e.target.value })
            }}
          />
        </Field>
        <Button type="submit" variant="outline">
          {t('manage.filter')}
        </Button>
      </form>
      <QueryState
        pending={query.isPending}
        error={query.isError}
        empty={query.data?.items.length === 0}
        retry={() => void query.refetch()}
      >
        <ul className="divide-y divide-border rounded-2xl border border-border">
          {query.data?.items.map((item) => (
            <li
              key={item.challengeDate}
              className="flex flex-wrap items-center justify-between gap-3 p-4 text-sm"
            >
              <div className="min-w-0 flex-1 break-words">
                <time className="font-medium" dateTime={item.challengeDate}>
                  {item.challengeDate}
                </time>
                <p>
                  {item.title} · {item.artistName}
                </p>
                <p className="text-muted-foreground">{t(`schedule.${item.source}`)}</p>
              </div>
              {item.locked ? (
                <span className="text-muted-foreground">{t('schedule.locked')}</span>
              ) : (
                <Button
                  variant="outline"
                  aria-label={t('schedule.changeNamed', { date: item.challengeDate })}
                  onClick={() => {
                    setDate(item.challengeDate)
                    setSelected(null)
                    setSaved(false)
                  }}
                >
                  {t('schedule.change')}
                </Button>
              )}
            </li>
          ))}
        </ul>
      </QueryState>
      <section className="space-y-3 border-t border-border pt-5" aria-label={t('schedule.assign')}>
        <h3 className="font-semibold">{t('schedule.assign')}</h3>
        <Field label={t('schedule.date')} htmlFor="challenge-date">
          <Input
            className="sm:max-w-xs"
            id="challenge-date"
            type="date"
            value={date}
            onChange={(e) => {
              setDate(e.target.value)
              setSaved(false)
            }}
          />
        </Field>
        {date && date <= today && (
          <p role="alert" className="text-sm text-negative">
            {t('manage.dailyLocked')}
          </p>
        )}
        <form
          className="flex items-end gap-2"
          role="search"
          aria-label={t('schedule.search')}
          onSubmit={(e) => {
            e.preventDefault()
            const data = new FormData(e.currentTarget)
            setQ(formText(data, 'q'))
            setPage(0)
          }}
        >
          <Field className="min-w-0 flex-1" label={t('schedule.search')} htmlFor="schedule-track">
            <Input id="schedule-track" name="q" type="search" maxLength={100} required />
          </Field>
          <Button type="submit" variant="outline">
            {t('manage.search')}
          </Button>
        </form>
        {q && (
          <QueryState
            pending={tracks.isPending}
            error={tracks.isError}
            empty={tracks.data?.items.length === 0}
            retry={() => void tracks.refetch()}
          >
            <ul className="divide-y divide-border">
              {tracks.data?.items.map((track) => (
                <li key={track.id} className="flex items-center justify-between gap-3 py-3">
                  <span className="min-w-0 flex-1 text-sm break-words">
                    {track.title} · {track.artistName}
                  </span>
                  <Button
                    variant="outline"
                    aria-pressed={selected?.id === track.id}
                    aria-label={t('schedule.pickNamed', { title: track.title })}
                    onClick={() => {
                      setSelected(track)
                      setSaved(false)
                    }}
                  >
                    {t(selected?.id === track.id ? 'schedule.selected' : 'schedule.pick')}
                  </Button>
                </li>
              ))}
            </ul>
            <PageNav page={page} total={tracks.data?.totalPages ?? 0} onChange={setPage} />
          </QueryState>
        )}
        {selected && (
          <p className="text-sm break-words">
            {t('schedule.chosen', { title: selected.title, artist: selected.artistName })}
          </p>
        )}
        <Button
          disabled={!selected || !date || date <= today}
          onClick={() => {
            mutation.reset()
            setConfirm(true)
          }}
        >
          {t('schedule.assign')}
        </Button>
        {saved && (
          <p role="status" className="text-sm text-positive">
            {t('manage.saved')}
          </p>
        )}
      </section>
      {confirm && selected && (
        <ConfirmChange
          title={t('schedule.assign')}
          description={t('schedule.confirm', {
            date,
            title: selected.title,
            artist: selected.artistName,
          })}
          pending={mutation.isPending}
          error={mutation.error}
          onClose={() => {
            setConfirm(false)
          }}
          onConfirm={() => {
            mutation.mutate(
              { type: 'schedule', date, trackId: selected.id },
              {
                onSuccess: () => {
                  setConfirm(false)
                  setSelected(null)
                  setSaved(true)
                },
              },
            )
          }}
        />
      )}
    </div>
  )
}
