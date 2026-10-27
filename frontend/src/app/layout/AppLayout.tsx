import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router-dom'
import { Toaster } from '@/shared/ui/Toast'
import { Footer } from './Footer'
import { Header } from './Header'

/** Frame shared by every route: skip link, header, page content, footer and the toast region. */
export function AppLayout() {
  const { t } = useTranslation('common')
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only rounded-full bg-primary px-4 py-2 font-semibold text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50"
      >
        {t('skipToContent')}
      </a>
      <Header />
      <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
        <Outlet />
      </main>
      <Footer />
      <Toaster />
    </div>
  )
}
