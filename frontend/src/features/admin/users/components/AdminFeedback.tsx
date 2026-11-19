import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Dialog } from '@/shared/ui/Dialog'
import { Button } from '@/shared/ui/Button'
import { ErrorState } from '@/shared/ui/ErrorState'
import { EmptyState } from '@/shared/ui/EmptyState'
import { Skeleton } from '@/shared/ui/Skeleton'
import { failureKey } from '../utils'
import { Pager } from '../../shell/fields'

export function QueryState({
  pending,
  error,
  empty,
  retry,
  children,
}: {
  pending: boolean
  error: boolean
  empty: boolean
  retry: () => void
  children?: ReactNode
}) {
  const { t } = useTranslation('admin')
  if (pending)
    return (
      <div role="status" aria-busy="true" className="space-y-2">
        <span className="sr-only">{t('loading')}</span>
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
      </div>
    )
  if (error) return <ErrorState onRetry={retry} />
  if (empty) return <EmptyState title={t('manage.empty')} description={t('manage.emptyHint')} />
  return children
}

export function PageNav({
  page,
  total,
  onChange,
}: {
  page: number
  total: number
  onChange: (page: number) => void
}) {
  const { t } = useTranslation('admin')
  return (
    <Pager
      page={page}
      totalPages={total}
      onChange={onChange}
      labels={{
        previous: t('pager.previous'),
        next: t('pager.next'),
        status: t('pager.status', { page: page + 1, total }),
      }}
    />
  )
}

/** Retains the proposed change and server error until confirmed or explicitly cancelled. */
export function ConfirmChange({
  title,
  description,
  pending,
  error,
  onClose,
  onConfirm,
  disabled,
  children,
}: {
  title: string
  description: string
  pending: boolean
  error: unknown
  onClose: () => void
  onConfirm: () => void
  disabled?: boolean
  children?: ReactNode
}) {
  const { t } = useTranslation('admin')
  return (
    <Dialog
      open
      title={title}
      description={description}
      onClose={() => {
        if (!pending) onClose()
      }}
    >
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          if (!pending && !disabled) onConfirm()
        }}
      >
        <fieldset disabled={pending} className="space-y-3">
          {children}
        </fieldset>
        {error ? (
          <p role="alert" className="text-sm text-negative">
            {t(failureKey(error))}
          </p>
        ) : null}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" disabled={pending} onClick={onClose}>
            {t('cancel')}
          </Button>
          <Button type="submit" disabled={pending || disabled}>
            {t(pending ? 'manage.saving' : 'manage.confirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
