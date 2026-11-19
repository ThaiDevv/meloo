import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { Field, Select } from '../../shell/fields'
import { ConfirmChange, PageNav, QueryState } from '../../users/components/AdminFeedback'
import { useModerationChange, useReports, type Report } from '../api/moderationApi'

export function Reports() {
  const { t, i18n } = useTranslation('admin')
  const [status, setStatus] = useState<Report['status']>('OPEN')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<{
    report: Report
    status: 'DISMISSED' | 'ACTIONED'
  } | null>(null)
  const [lock, setLock] = useState(false)
  const [reason, setReason] = useState('')
  const [saved, setSaved] = useState(false)
  const query = useReports(status, page)
  const mutation = useModerationChange()
  return (
    <div className="space-y-4">
      <Field label={t('reports.status')} htmlFor="report-status">
        <Select
          id="report-status"
          className="sm:max-w-xs"
          value={status}
          onChange={(e) => {
            const value = e.target.value
            if (value === 'OPEN' || value === 'DISMISSED' || value === 'ACTIONED') {
              setStatus(value)
              setPage(0)
            }
          }}
        >
          {(['OPEN', 'DISMISSED', 'ACTIONED'] as const).map((value) => (
            <option key={value} value={value}>
              {t(`reports.${value}`)}
            </option>
          ))}
        </Select>
      </Field>
      {saved && (
        <p role="status" className="text-sm text-positive">
          {t('manage.saved')}
        </p>
      )}
      <QueryState
        pending={query.isPending}
        error={query.isError}
        empty={query.data?.items.length === 0}
        retry={() => void query.refetch()}
      >
        <ul className="divide-y divide-border rounded-2xl border border-border">
          {query.data?.items.map((report) => (
            <li key={report.id} className="space-y-3 p-4">
              <div className="flex flex-wrap justify-between gap-2 text-sm">
                <span className="font-medium break-all">
                  {report.senderNickname} · {t(`reports.${report.status}`)}
                </span>
                <time className="text-muted-foreground" dateTime={report.createdAt}>
                  {new Date(report.createdAt).toLocaleString(i18n.language)}
                </time>
              </div>
              <blockquote className="rounded-xl bg-raised p-3 text-sm whitespace-pre-wrap break-words">
                {report.messageText}
              </blockquote>
              <p className="text-xs text-muted-foreground break-all">
                {t('reports.reporter', { id: report.reporterId })}
              </p>
              <details>
                <summary className="cursor-pointer text-sm font-medium">
                  {t('reports.context', { count: report.context.length })}
                </summary>
                <ol className="mt-2 space-y-2 text-sm">
                  {report.context.map((message) => (
                    <li key={message.messageId} className="break-words">
                      <span className="font-medium">{message.senderNickname}</span>{' '}
                      <time dateTime={message.sentAt} className="text-xs text-muted-foreground">
                        {new Date(message.sentAt).toLocaleString(i18n.language)}
                      </time>
                      <p className="whitespace-pre-wrap">{message.content}</p>
                    </li>
                  ))}
                </ol>
              </details>
              {report.handledBy && (
                <p className="text-xs text-muted-foreground break-all">
                  {t('reports.handled', {
                    id: report.handledBy,
                    time: report.handledAt
                      ? new Date(report.handledAt).toLocaleString(i18n.language)
                      : t('manage.none'),
                  })}
                </p>
              )}
              {report.status === 'OPEN' && (
                <div className="flex flex-wrap gap-2">
                  {(['DISMISSED', 'ACTIONED'] as const).map((next) => (
                    <Button
                      key={next}
                      variant={next === 'ACTIONED' ? 'outline' : 'ghost'}
                      onClick={() => {
                        mutation.reset()
                        setSaved(false)
                        setLock(false)
                        setReason('')
                        setSelected({ report, status: next })
                      }}
                    >
                      {t(`reports.${next === 'ACTIONED' ? 'resolve' : 'dismiss'}`)}
                    </Button>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ul>
        <PageNav page={page} total={query.data?.totalPages ?? 0} onChange={setPage} />
      </QueryState>
      {selected && (
        <ConfirmChange
          title={t(selected.status === 'ACTIONED' ? 'reports.resolve' : 'reports.dismiss')}
          description={t('reports.confirm', {
            name: selected.report.senderNickname,
            message: selected.report.messageText,
          })}
          pending={mutation.isPending}
          error={mutation.error}
          disabled={lock && !reason.trim()}
          onClose={() => {
            setSelected(null)
          }}
          onConfirm={() => {
            mutation.mutate(
              {
                type: 'resolve',
                id: selected.report.id,
                body: {
                  status: selected.status,
                  lockSender: lock,
                  lockReason: lock ? reason.trim() : null,
                },
              },
              {
                onSuccess: () => {
                  setSelected(null)
                  setSaved(true)
                },
              },
            )
          }}
        >
          {selected.status === 'ACTIONED' && (
            <>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={lock}
                  onChange={(e) => {
                    setLock(e.target.checked)
                  }}
                />
                {t('reports.lockSender')}
              </label>
              {lock && (
                <Field label={t('users.lockReason')} htmlFor="report-lock-reason">
                  <Input
                    id="report-lock-reason"
                    required
                    maxLength={500}
                    value={reason}
                    onChange={(e) => {
                      setReason(e.target.value)
                    }}
                  />
                </Field>
              )}
            </>
          )}
        </ConfirmChange>
      )}
    </div>
  )
}
