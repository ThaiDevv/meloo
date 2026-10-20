import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

export function NotFoundPage() {
  const { t } = useTranslation('common')
  return (
    <section className="mx-auto max-w-xl p-6 text-center">
      <h1 className="text-xl font-semibold">{t('notFound.title')}</h1>
      <Link to="/" className="mt-4 inline-block underline">
        {t('notFound.back')}
      </Link>
    </section>
  )
}
