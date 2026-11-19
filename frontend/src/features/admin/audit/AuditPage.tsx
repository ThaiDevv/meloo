import { toUtc } from './dates'
import { formText, UUID_PATTERN } from '../users/utils'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { Field } from '../shell/fields'
import { PageNav, QueryState } from '../users/components/AdminFeedback'
import { useAudit } from './api/auditApi'

export function AuditPage() {
  const { t, i18n } = useTranslation('admin')
  const [filters, setFilters] = useState<Parameters<typeof useAudit>[0]>({ page: 0 })
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const query = useAudit(filters)
  return (
    <section className="space-y-5" aria-label={t('shell.nav.audit')}>
      <header>
        <h2 className="text-xl font-semibold">{t('shell.nav.audit')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('audit.subtitle')}</p>
      </header>
      <form
        role="search"
        className="grid gap-3 rounded-2xl border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-3"
        onSubmit={(e) => {
          e.preventDefault()
          const data = new FormData(e.currentTarget)
          const text = (name: string) => formText(data, name) || undefined
          setFilters({
            page: 0,
            actorId: text('actorId'),
            action: text('action'),
            targetType: text('targetType'),
            from: toUtc(from),
            to: toUtc(to),
          })
        }}
      >
        <Field label={t('audit.actor')} htmlFor="audit-actor">
          <Input id="audit-actor" name="actorId" pattern={UUID_PATTERN} maxLength={36} />
        </Field>
        <Field label={t('audit.action')} htmlFor="audit-action">
          <Input id="audit-action" name="action" maxLength={60} placeholder="USER_LOCKED" />
        </Field>
        <Field label={t('audit.targetType')} htmlFor="audit-target">
          <Input id="audit-target" name="targetType" maxLength={40} placeholder="USER" />
        </Field>
        <Field label={t('audit.from')} htmlFor="audit-from">
          <Input
            id="audit-from"
            type="datetime-local"
            max={to || undefined}
            value={from}
            onChange={(e) => {
              setFrom(e.target.value)
            }}
          />
        </Field>
        <Field label={t('audit.to')} htmlFor="audit-to">
          <Input
            id="audit-to"
            type="datetime-local"
            min={from || undefined}
            value={to}
            onChange={(e) => {
              setTo(e.target.value)
            }}
          />
        </Field>
        <div className="flex items-end gap-2">
          <Button type="submit">{t('manage.filter')}</Button>
          <Button
            type="reset"
            variant="ghost"
            onClick={() => {
              setFrom('')
              setTo('')
              setFilters({ page: 0 })
            }}
          >
            {t('manage.clear')}
          </Button>
        </div>
      </form>
      <QueryState
        pending={query.isPending}
        error={query.isError}
        empty={query.data?.items.length === 0}
        retry={() => void query.refetch()}
      >
        <ol className="divide-y divide-border rounded-2xl border border-border">
          {query.data?.items.map((entry) => (
            <li key={entry.id} className="space-y-2 p-4 text-sm">
              <div className="flex flex-wrap justify-between gap-2">
                <span className="font-medium break-all">{entry.action}</span>
                <time className="text-muted-foreground tabular-nums" dateTime={entry.createdAt}>
                  {new Date(entry.createdAt).toLocaleString(i18n.language)}
                </time>
              </div>
              <p className="break-all">
                {t('audit.by', { name: entry.actorNickname ?? entry.actorId })}
              </p>
              <p className="text-muted-foreground break-all">
                {entry.targetType} · {entry.targetId ?? t('manage.none')}
              </p>
              {entry.reason && (
                <p className="whitespace-pre-wrap break-words">
                  {t('audit.reason', { reason: entry.reason })}
                </p>
              )}
              <details>
                <summary className="cursor-pointer text-muted-foreground">
                  {t('audit.detail', { id: entry.id })}
                </summary>
                <dl className="mt-2 space-y-2">
                  <div>
                    <dt>{t('audit.actor')}</dt>
                    <dd className="break-all">{entry.actorId}</dd>
                  </div>
                </dl>
                <pre className="mt-2 max-h-80 overflow-auto rounded-xl bg-raised p-3 text-xs whitespace-pre-wrap break-all">
                  {JSON.stringify(entry.detail, null, 2)}
                </pre>
              </details>
            </li>
          ))}
        </ol>
        <PageNav
          page={filters.page ?? 0}
          total={query.data?.totalPages ?? 0}
          onChange={(page) => {
            setFilters({ ...filters, page })
          }}
        />
      </QueryState>
    </section>
  )
}
