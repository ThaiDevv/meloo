import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES } from '@/i18n'
import { Button } from '@/shared/ui/Button'

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation('common')
  const current = i18n.resolvedLanguage
  return (
    <div role="group" aria-label={t('language.label')} className="flex gap-1">
      {SUPPORTED_LANGUAGES.map((lng) => (
        <Button
          key={lng}
          size="sm"
          variant={current === lng ? 'default' : 'ghost'}
          aria-pressed={current === lng}
          lang={lng}
          onClick={() => {
            void i18n.changeLanguage(lng)
          }}
        >
          {lng.toUpperCase()}
        </Button>
      ))}
    </div>
  )
}
