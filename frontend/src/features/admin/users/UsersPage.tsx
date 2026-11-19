import { formText } from './utils'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { Badge } from '@/shared/ui/Badge'
import { Field } from '../shell/fields'
import { useAdminUsers } from './api/usersApi'
import { PageNav, QueryState } from './components/AdminFeedback'
import { UserDetail } from './components/UserDetail'

export function UsersPage() {
  const { t } = useTranslation('admin')
  const [filters, setFilters] = useState<Parameters<typeof useAdminUsers>[0]>({ page: 0 })
  const [selected, setSelected] = useState<string | null>(null)
  const query = useAdminUsers(filters)
  return (
    <section className="space-y-5" aria-label={t('shell.nav.users')}>
      <header>
        <h2 className="text-xl font-semibold">{t('shell.nav.users')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('users.subtitle')}</p>
      </header>
      <form
        role="search"
        className="flex flex-wrap items-end gap-3 rounded-2xl border border-border bg-surface p-4"
        onSubmit={(e) => {
          e.preventDefault()
          const form = new FormData(e.currentTarget)
          setFilters({ page: 0, q: formText(form, 'q') || undefined })
          setSelected(null)
        }}
      >
        <Field label={t('users.search')} htmlFor="users-q" className="min-w-0 flex-1">
          <Input id="users-q" name="q" type="search" maxLength={100} />
        </Field>
        <Button type="submit" className="justify-self-start">
          {t('manage.filter')}
        </Button>
      </form>
      <QueryState
        pending={query.isPending}
        error={query.isError}
        empty={query.data?.items.length === 0}
        retry={() => void query.refetch()}
      >
        <ul
          className="divide-y divide-border rounded-2xl border border-border"
          aria-busy={query.isFetching}
        >
          {query.data?.items.map((user) => (
            <li key={user.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div className="min-w-0 flex-1 basis-full break-words sm:basis-auto">
                <p className="font-medium">{user.nickname}</p>
                <p className="text-sm text-muted-foreground break-all">
                  {user.email ?? t('users.values.GUEST')} · {t(`users.values.${user.role}`)}
                </p>
              </div>
              <Badge tone={user.status === 'LOCKED' ? 'negative' : 'positive'}>
                {t(`users.values.${user.status}`)}
              </Badge>
              <Button
                variant="outline"
                aria-label={t('users.viewNamed', { name: user.nickname })}
                onClick={() => {
                  setSelected(user.id)
                }}
              >
                {t('users.view')}
              </Button>
            </li>
          ))}
        </ul>
        <PageNav
          page={filters.page ?? 0}
          total={query.data?.totalPages ?? 0}
          onChange={(page) => {
            setFilters({ ...filters, page })
            setSelected(null)
          }}
        />
      </QueryState>
      {selected && (
        <UserDetail
          key={selected}
          id={selected}
          onClose={() => {
            setSelected(null)
          }}
        />
      )}
    </section>
  )
}
