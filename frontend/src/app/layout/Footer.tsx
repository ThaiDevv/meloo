import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

const linkClass = 'rounded underline-offset-4 hover:text-foreground hover:underline'

export function Footer() {
  const { t } = useTranslation('common')
  return (
    <footer className="border-t border-border">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-3 px-4 py-6 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
        <p>{t('footer.copyright', { year: new Date().getFullYear() })}</p>
        <nav aria-label={t('footer.label')}>
          <ul className="flex flex-wrap gap-x-6 gap-y-2">
            <li>
              <Link to="/legal/privacy" className={linkClass}>
                {t('footer.privacy')}
              </Link>
            </li>
            <li>
              <Link to="/legal/terms" className={linkClass}>
                {t('footer.terms')}
              </Link>
            </li>
          </ul>
        </nav>
      </div>
    </footer>
  )
}
