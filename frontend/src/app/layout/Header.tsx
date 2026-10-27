import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { AccountMenu } from '@/features/auth/AccountMenu'
import { Logo } from '@/shared/ui/Logo'
import { ThemeToggle } from '@/shared/ui/ThemeToggle'
import { LanguageSwitcher } from '../LanguageSwitcher'

export function Header() {
  const { t } = useTranslation('common')
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between gap-2 px-4">
        <Link to="/" aria-label={t('appName')} className="rounded-lg">
          <Logo />
        </Link>
        <div className="flex items-center gap-1">
          <LanguageSwitcher />
          <ThemeToggle />
          <AccountMenu />
        </div>
      </div>
    </header>
  )
}
