import { TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/utils'
import { Button } from './Button'

interface ErrorStateProps {
  title?: string
  description?: string
  /** Shows the retry button when given. */
  onRetry?: () => void
  className?: string
}

/** Sits inside the block that failed, never as a page-wide banner. */
export function ErrorState({ title, description, onRetry, className }: ErrorStateProps) {
  const { t } = useTranslation('common')
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center gap-3 rounded-2xl border border-border bg-surface px-6 py-10 text-center',
        className,
      )}
    >
      <TriangleAlert aria-hidden="true" className="size-5 text-negative" strokeWidth={1.75} />
      <p className="text-base font-semibold">{title ?? t('errorState.title')}</p>
      <p className="max-w-sm text-sm text-muted-foreground">
        {description ?? t('errorState.description')}
      </p>
      {onRetry ? (
        <Button variant="outline" onClick={onRetry}>
          {t('ui.retry')}
        </Button>
      ) : null}
    </div>
  )
}
