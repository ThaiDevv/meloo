import { useTranslation } from 'react-i18next'
import { Link, Outlet } from 'react-router-dom'
import { LanguageSwitcher } from './LanguageSwitcher'

export function AppLayout() {
  const { t } = useTranslation('common')
  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between border-b border-border px-4 py-3">
        <Link to="/" className="text-lg font-bold">
          {t('appName')}
        </Link>
        <LanguageSwitcher />
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
