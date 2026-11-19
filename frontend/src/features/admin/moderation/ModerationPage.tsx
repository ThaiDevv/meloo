import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Reports } from './components/Reports'
import { BannedWords } from './components/BannedWords'
import { DailySchedule } from './components/DailySchedule'

export function ModerationPage() {
  const { t } = useTranslation('admin')
  const [view, setView] = useState<'reports' | 'words' | 'schedule'>('reports')
  return (
    <section className="space-y-5" aria-label={t('shell.nav.moderation')}>
      <header>
        <h2 className="text-xl font-semibold">{t('shell.nav.moderation')}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('moderation.subtitle')}</p>
      </header>
      <div className="flex flex-wrap gap-2" role="group" aria-label={t('moderation.sections')}>
        {(['reports', 'words', 'schedule'] as const).map((key) => (
          <Button
            key={key}
            variant={key === view ? 'default' : 'outline'}
            aria-pressed={key === view}
            onClick={() => {
              setView(key)
            }}
          >
            {t(`moderation.${key}`)}
          </Button>
        ))}
      </div>
      {view === 'reports' ? <Reports /> : view === 'words' ? <BannedWords /> : <DailySchedule />}
    </section>
  )
}
